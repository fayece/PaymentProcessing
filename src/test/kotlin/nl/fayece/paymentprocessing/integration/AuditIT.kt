package nl.fayece.paymentprocessing.integration

import nl.fayece.paymentprocessing.domain.Account
import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.repositories.AccountRepository
import nl.fayece.paymentprocessing.repositories.IdempotencyKeyRepository
import nl.fayece.paymentprocessing.repositories.TransactionRepository
import nl.fayece.paymentprocessing.repositories.TransactionStatusHistoryRepository
import nl.fayece.paymentprocessing.util.toMoney
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
import java.util.Currency
import java.util.UUID

class AuditIT : IntegrationTest() {

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
    private val eur = Currency.getInstance("EUR")

    private lateinit var sourceAccount: Account
    private lateinit var destinationAccount: Account

    @BeforeEach
    fun setup() {
        statusHistoryRepository.deleteAll()
        transactionRepository.deleteAll()
        idempotencyKeyRepository.deleteAll()
        accountRepository.deleteAll()

        val (src, dst) = accountRepository.saveAll(listOf(
        Account(name = "Alice", iban = sourceIban, balance = BigDecimal("500.00").toMoney()),
        Account(name = "Bob", iban = destinationIban, balance = BigDecimal("100.00").toMoney())
        ))

        sourceAccount = src
        destinationAccount = dst
    }

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
    inner class GetPaymentHistory {

        @Test
        fun `returns status history for a transaction in order`() {

            val createResponse = restTemplate.postForEntity(
                "/api/payments",
                HttpEntity(validPayload(), HttpHeaders().apply {
                    set("Idempotency-Key", UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                }),
                Map::class.java
            )

            val transactionId = createResponse.body!!["transactionId"]

            val response = restTemplate.getForEntity(
                "/api/audit/payments/$transactionId/history",
                List::class.java
            )

            assert(response.statusCode == HttpStatus.OK)
            @Suppress("UNCHECKED_CAST")
            val history = response.body as List<Map<String, Any>>
            assert(history.size == 3)
            assert(history[0]["status"] == "INITIATED")
            assert(history[1]["status"] == "VALIDATED")
            assert(history[2]["status"] == "QUEUED")
        }

        @Test
        fun `returns 400 for invalid transaction ID`() {
            val response = restTemplate.getForEntity(
                "/api/audit/payments/invalid-id/history",
                Map::class.java
            )

            assert(response.statusCode == HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `returns 404 for non-existing transaction`() {
            val response = restTemplate.getForEntity(
                "/api/audit/payments/${UUID.randomUUID()}/history",
                Map::class.java
            )

            assert(response.statusCode == HttpStatus.NOT_FOUND)
        }
    }

    @Nested
    inner class GetFailedPayments {

        @Test
        fun `returns all failed transactions`() {

            val createResponse = restTemplate.postForEntity(
                "/api/payments",
                HttpEntity(validPayload(amount = "9999.00"), HttpHeaders().apply {
                    set("Idempotency-Key", UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                }),
                Map::class.java
            )

            val succesful = restTemplate.postForEntity(
                "/api/payments",
                HttpEntity(validPayload(), HttpHeaders().apply {
                    set("Idempotency-Key", UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                }),
                Map::class.java
            )

            val successfulId = succesful.body!!["transactionId"]

            val response = restTemplate.getForEntity("/api/audit/payments/failed", List::class.java)

            assert(response.statusCode == HttpStatus.OK)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as List<Map<String, Any>>
            assert(body.size == 1)
            assert(body[0]["transactionId"] != successfulId)
        }

        @Test
        fun `returns empty list when no failed transactions exist`() {
            val response = restTemplate.getForEntity(
                "/api/audit/payments/failed",
                List::class.java
            )

            assert(response.statusCode == HttpStatus.OK)
            assert(response.body?.isEmpty() == true)
        }
    }
}