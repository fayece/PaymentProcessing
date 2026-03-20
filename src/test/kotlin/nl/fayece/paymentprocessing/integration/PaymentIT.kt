package nl.fayece.paymentprocessing.integration

import nl.fayece.paymentprocessing.domain.AccountStatus
import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.repositories.AccountRepository
import nl.fayece.paymentprocessing.repositories.TransactionRepository
import nl.fayece.paymentprocessing.repositories.TransactionStatusHistoryRepository
import nl.fayece.paymentprocessing.util.toMoney
import nl.fayece.paymentprocessing.domain.Account
import nl.fayece.paymentprocessing.repositories.IdempotencyKeyRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.util.UUID

class PaymentIT() : IntegrationTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var accountRepository: AccountRepository

    @Autowired
    lateinit var transactionRepository: TransactionRepository

    @Autowired
    lateinit var statusHistoryRepository: TransactionStatusHistoryRepository

    @Autowired
    lateinit var idempotencyKeyRepository: IdempotencyKeyRepository

    private val sourceIban = Iban.of("NL13TEST0123456789")
    private val destinationIban = Iban.of("NL65TEST0987656789")

    @BeforeEach
    fun setup() {
        statusHistoryRepository.deleteAll()
        transactionRepository.deleteAll()
        idempotencyKeyRepository.deleteAll()
        accountRepository.deleteAll()

        accountRepository.saveAll(listOf(
            Account(name = "Alice", iban = sourceIban, balance = BigDecimal("500.00").toMoney()),
            Account(name = "Bob", iban = destinationIban, balance = BigDecimal("100.00").toMoney())
        ))
    }

    private fun postPayment(body: String, idempotencyKey: String = UUID.randomUUID().toString()) = restTemplate.postForEntity(
        "/api/payments",
        HttpEntity(body, HttpHeaders().apply {
            set("Idempotency-Key", idempotencyKey)
            contentType = MediaType.APPLICATION_JSON }),
        Map::class.java
    )

    private fun validPayload(
        sourceIban: String = this.sourceIban.value,
        destinationIban: String = this.destinationIban.value,
        amount: String = "100.00",
        currency: String = "EUR"
    ) = """
        {
            "sourceIban": "$sourceIban",
            "destinationIban": "$destinationIban",
            "amount": $amount,
            "currency": "$currency"
        }
    """.trimIndent()

    @Nested
    inner class SubmitPayment {

        @Nested
        inner class HappyPath {

            @Test
            fun `returns 201 and transaction reaches QUEUED`() {
                val response = postPayment(validPayload())

                println("Status: ${response.statusCode}")
                println("Body: ${response.body}")

                assert(response.statusCode == HttpStatus.CREATED)
                assert(response.body?.get("status") == "QUEUED")
                assert(response.body?.get("transactionId") != null)
                assert(response.body?.get("createdAt") != null)
            }

            @Test
            fun `source account is debited`() {
                postPayment(validPayload())

                val source = accountRepository.findByIban(sourceIban).get()
                assert(source.balance == BigDecimal("400.00").toMoney())
            }

            @Test
            fun `destination account is credited`() {
                postPayment(validPayload())

                val destination = accountRepository.findByIban(destinationIban).get()
                assert(destination.balance == BigDecimal("200.00").toMoney())
            }

            @Test
            fun `transaction status history is recorded`() {
                postPayment(validPayload())

                val transaction = transactionRepository.findAll().first()
                val history = statusHistoryRepository.findAll()
                    .filter { it.transaction.id == transaction.id }
                    .map { it.status }

                assert(history == listOf(
                    TransactionStatus.INITIATED,
                    TransactionStatus.VALIDATED,
                    TransactionStatus.QUEUED
                ))
            }
        }

        @Nested
        inner class Idempotency {

            @Test
            fun `returns 400 when idempotency key is missing`() {
                val response = restTemplate.postForEntity(
                    "/api/payments",
                    HttpEntity(validPayload(), HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                    Map::class.java
                )

                assert(response.statusCode == HttpStatus.BAD_REQUEST)
            }

            @Test
            fun `duplicate submission returns same response without creating second transaction`() {
                val key = UUID.randomUUID().toString()

                val first = postPayment(validPayload(), idempotencyKey = key)
                val second = postPayment(validPayload(), idempotencyKey = key)

                assert(first.statusCode == HttpStatus.CREATED)
                assert(second.statusCode == HttpStatus.CREATED)
                assert(first.body?.get("transactionId") == second.body?.get("transactionId"))
                assert(transactionRepository.count() == 1L)  // Check that only one transaction was created
            }

            @Test
            fun `duplicate submission does not debit source account twice`() {
                val key = UUID.randomUUID().toString()

                postPayment(validPayload(), idempotencyKey = key)
                postPayment(validPayload(), idempotencyKey = key)

                val source = accountRepository.findByIban(sourceIban).get()
                assert(source.balance == BigDecimal("400.00").toMoney())
            }
        }

        @Nested
        inner class Validation {

            @Test
            fun `returns 400 for invalid source IBAN`() {
                val response = postPayment(validPayload(sourceIban = "INVALID"))

                assert(response.statusCode == HttpStatus.BAD_REQUEST)
            }

            @Test
            fun `returns 400 for invalid destination IBAN`() {
                val response = postPayment(validPayload(destinationIban = "INVALID"))

                assert(response.statusCode == HttpStatus.BAD_REQUEST)
            }

            @Test
            fun `returns 400 for negative amount`() {
                val response = postPayment(validPayload(amount = "-50.00"))

                assert(response.statusCode == HttpStatus.BAD_REQUEST)
            }

            @Test
            fun `returns 422 for insufficient funds`() {
                val response = postPayment(validPayload(amount = "9999.00"))

                assert(response.statusCode == HttpStatus.UNPROCESSABLE_CONTENT)
            }

            @Test
            fun `returns 400 when source and destination IBAN are the same`() {
                val response = postPayment(validPayload(destinationIban = sourceIban.value))

                assert(response.statusCode == HttpStatus.BAD_REQUEST)
            }

            @Test
            fun `returns 404 when source account does not exist`() {
                val response = postPayment(validPayload(sourceIban = "NL02ABNA0123456789"))

                assert(response.statusCode == HttpStatus.NOT_FOUND)
            }

            @Test
            fun `returns 400 when source account is frozen`() {
                val source = accountRepository.findByIban(sourceIban).get()
                source.status = AccountStatus.FROZEN
                accountRepository.save(source)

                val response = postPayment(validPayload())

                assert(response.statusCode == HttpStatus.BAD_REQUEST)
            }
        }
    }
}