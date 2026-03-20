package nl.fayece.paymentprocessing.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import jakarta.persistence.EntityNotFoundException
import nl.fayece.paymentprocessing.domain.*
import nl.fayece.paymentprocessing.domain.exceptions.InvalidTransactionStateException
import nl.fayece.paymentprocessing.domain.exceptions.UnauthorizedOperationException
import nl.fayece.paymentprocessing.dto.payments.PaymentRequest
import nl.fayece.paymentprocessing.dto.payments.PaymentResponse
import nl.fayece.paymentprocessing.dto.payments.ReversePaymentRequest
import nl.fayece.paymentprocessing.services.IdempotencyService
import nl.fayece.paymentprocessing.services.PaymentService
import nl.fayece.paymentprocessing.util.toMoney
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.Currency
import java.util.UUID

@WebMvcTest(PaymentController::class)
class PaymentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var paymentService: PaymentService

    @MockkBean
    lateinit var idempotencyService: IdempotencyService

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private val eur = Currency.getInstance("EUR")
    private val sourceIban = Iban.of("NL13TEST0123456789")
    private val destinationIban = Iban.of("NL65TEST0987656789")
    private val idempotencyKey = "test-idempotency-key"

    private val sourceAccount = Account(name = "Alice", iban = sourceIban, balance = BigDecimal("500.00").toMoney())
    private val destinationAccount = Account(name = "Bob", iban = destinationIban, balance = BigDecimal("100.00").toMoney())

    private val validRequest = PaymentRequest(
        sourceIban = sourceIban.value,
        destinationIban = destinationIban.value,
        amount = BigDecimal("100.00"),
        currency = eur.currencyCode
    )

    private fun queuedTransaction() = Transaction.create(sourceAccount, destinationAccount, BigDecimal("100.00"), eur).also {
        it.transitionTo(TransactionStatus.VALIDATED)
        it.transitionTo(TransactionStatus.QUEUED)
    }

    private fun settledTransaction() = Transaction.create(sourceAccount, destinationAccount, BigDecimal("100.00"), eur).also {
        it.transitionTo(TransactionStatus.VALIDATED)
        it.transitionTo(TransactionStatus.QUEUED)
        it.transitionTo(TransactionStatus.PENDING)
        it.transitionTo(TransactionStatus.SETTLED)
    }

    @BeforeEach
    fun setupIdempotencyInit() {

        every { idempotencyService.resolve(
            eq(idempotencyKey), any(), eq(PaymentResponse::class.java), any<() -> PaymentResponse>()) } answers {
            @Suppress("UNCHECKED_CAST")
            (args[3] as () -> PaymentResponse).invoke()
        }

        every { idempotencyService.exists(any()) } returns false
        every { idempotencyService.record(any(), any()) } just Runs
    }

    @Nested
    inner class SubmitPayment {

        @Nested
        inner class HappyPath {

            @Test
            fun `returns 201 with transaction response`() {
                every { paymentService.submitPayment(any(), any(), any(), any())} returns queuedTransaction()

                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.transactionId") { exists() }
                    jsonPath("$.sourceIban") { value(sourceIban.value) }
                    jsonPath("$.destinationIban") { value(destinationIban.value) }
                    jsonPath("$.amount") { value(100.00) }
                    jsonPath("$.currency") { value("EUR") }
                    jsonPath("$.status") { value("QUEUED") }
                    jsonPath("$.createdAt") { exists() }
                }
            }
        }

        @Nested
        inner class Idempotency {

            @Test
            fun `returns 400 when idempotency key is missing`() {
                mockMvc.post("/api/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("Required header 'Idempotency-Key' is missing") }
                }
            }

            @Test
            fun `returns cached response on duplicate key without reprocessing`() {
                val cachedResponse = PaymentResponse.from(queuedTransaction())
                every { idempotencyService.resolve(
                    eq(idempotencyKey), any(), PaymentResponse::class.java, any<() -> PaymentResponse>()) } returns cachedResponse

                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.transactionId") { value(cachedResponse.transactionId.toString()) }
                }

                verify(exactly = 0) { paymentService.submitPayment(any(), any(), any(), any()) }
            }
        }

        @Nested
        inner class Validation {

            @Test
            fun `returns 400 when source IBAN format is invalid`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(sourceIban = "INVALID"))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { exists() }
                    jsonPath("$.fields[?(@.field == 'sourceIban')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when destination IBAN format is invalid`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(destinationIban = "INVALID"))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { exists() }
                    jsonPath("$.fields[?(@.field == 'destinationIban')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when source IBAN is blank`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(sourceIban = ""))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'sourceIban')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when destination IBAN is blank`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(destinationIban = ""))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'destinationIban')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when amount is null`() {
                val requestWithNullAmount = """
                    {
                        "sourceIban": "${validRequest.sourceIban}",
                        "destinationIban": "${validRequest.destinationIban}",
                        "amount": null,
                        "currency": "${validRequest.currency}"
                    }
                """.trimIndent()

                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = requestWithNullAmount
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'amount')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when amount is zero`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(amount = BigDecimal.ZERO))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'amount')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when amount is negative`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(amount = BigDecimal("-10.00")))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'amount')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when currency is blank`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(currency = ""))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'currency')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 with all field errors when multiple fields are invalid`() {
                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(
                        validRequest.copy(sourceIban = "INVALID", amount = BigDecimal.ZERO)
                    )
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields") { isArray() }
                    jsonPath("$.fields[?(@.field == 'sourceIban')]") { isNotEmpty() }
                    jsonPath("$.fields[?(@.field == 'amount')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when service rejects the request`() {
                every { paymentService.submitPayment(any(), any(), any(), any()) } throws
                        IllegalArgumentException("Payment amount must be positive")

                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("Payment amount must be positive") }
                }
            }

            @Test
            fun `returns 404 when account is not found`() {
                every { paymentService.submitPayment(any(), any(), any(), any()) } throws
                        EntityNotFoundException("Account not found: ${sourceIban.value}")

                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.message") { value("Account not found: ${sourceIban.value}") }
                }
            }
        }

        @Nested
        inner class FailureHandling {

            @Test
            fun `returns 503 with Retry-After header on optimistic locking failure`() {
                every { paymentService.submitPayment(any(), any(), any(), any()) } throws
                        ObjectOptimisticLockingFailureException(Account::class.java, "test-id")

                mockMvc.post("/api/payments") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isServiceUnavailable() }
                    header { string("Retry-After", "1") }
                }
            }
        }
    }

    @Nested
    inner class ConfirmSettlement {

        @Nested
        inner class HappyPath {

            @Test
            fun `returns 204 on successful settlement`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.handleSettlementConfirmation(transactionId) } just Runs

                mockMvc.post("/api/payments/$transactionId/confirm-settlement") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isNoContent() }
                }
            }
        }

        @Nested
        inner class Idempotency {

            @Test
            fun `returns 400 when idempotency key is missing`() {
                val transactionId = UUID.randomUUID()

                mockMvc.post("/api/payments/$transactionId/confirm-settlement") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("Required header 'Idempotency-Key' is missing") }
                }
            }

            @Test
            fun `returns 204 on duplicate key without reprocessing`() {
                val transactionId = UUID.randomUUID()
                every { idempotencyService.exists(idempotencyKey) } returns true

                mockMvc.post("/api/payments/$transactionId/confirm-settlement") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isNoContent() }
                }

                verify(exactly = 0) { paymentService.handleSettlementConfirmation(any()) }
            }
        }

        @Nested
        inner class FailureHandling {

            @Test
            fun `returns 400 when transaction ID is invalid`() {
                val invalidId = "invalid-id"

                mockMvc.post("/api/payments/$invalidId/confirm-settlement") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isBadRequest() }
                }

            }

            @Test
            fun `returns 404 when transaction is not found`() {
                val unknownId = UUID.randomUUID()
                every { paymentService.handleSettlementConfirmation(unknownId) } throws
                        EntityNotFoundException("Transaction not found: $unknownId")

                mockMvc.post("/api/payments/$unknownId/confirm-settlement") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isNotFound() }
                }
            }

            @Test
            fun `returns 422 when transaction is not in a valid state for settlement`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.handleSettlementConfirmation(transactionId) } throws
                        InvalidTransactionStateException("Transaction is not in a valid state for settlement")

                mockMvc.post("/api/payments/$transactionId/confirm-settlement") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isUnprocessableContent() }
                }
            }

            @Test
            fun `returns 503 with Retry-After header on optimistic locking failure`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.handleSettlementConfirmation(transactionId) } throws
                        ObjectOptimisticLockingFailureException(Transaction::class.java, "test-id")

                mockMvc.post("/api/payments/$transactionId/confirm-settlement") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isServiceUnavailable() }
                    header { string("Retry-After", "1") }
                }
            }
        }
    }

    @Nested
    inner class RefundPayment {

        @Nested
        inner class HappyPath {

            @Test
            fun `returns 201 with refund transaction response`() {

                val transactionId = UUID.randomUUID()
                every { paymentService.refundPayment(transactionId) } returns settledTransaction()

                mockMvc.post("/api/payments/$transactionId/refund") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.transactionId") { exists() }
                    jsonPath("$.sourceIban") { value(sourceIban.value) }
                    jsonPath("$.destinationIban") { value(destinationIban.value) }
                    jsonPath("$.amount") { value(100.00) }
                    jsonPath("$.currency") { value("EUR") }
                }
            }
        }

        @Nested
        inner class Idempotency {

            @Test
            fun `returns 400 when idempotency key is missing`() {
                val transactionId = UUID.randomUUID()

                mockMvc.post("/api/payments/$transactionId/refund") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("Required header 'Idempotency-Key' is missing") }
                }
            }

            @Test
            fun `returns cached response on duplicate key without reprocessing`() {
                val transactionId = UUID.randomUUID()
                val cachedResponse = PaymentResponse.from(settledTransaction())
                every { idempotencyService.resolve(
                    eq(idempotencyKey), transactionId, PaymentResponse::class.java, any<() -> PaymentResponse>()
                ) } returns cachedResponse

                mockMvc.post("/api/payments/$transactionId/refund") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.transactionId") { value(cachedResponse.transactionId.toString()) }
                }

                verify(exactly = 0) { paymentService.refundPayment(any()) }
            }
        }

        @Nested
        inner class FailureHandling {

            @Test
            fun `returns 400 when transaction ID is invalid`() {
                val invalidId = "invalid-id"

                mockMvc.post("/api/payments/$invalidId/refund") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isBadRequest() }
                }
            }

            @Test
            fun `returns 404 when transaction is not found`() {
                val unknownId = UUID.randomUUID()
                every { paymentService.refundPayment(unknownId) } throws
                        EntityNotFoundException("Transaction not found: $unknownId")

                mockMvc.post("/api/payments/$unknownId/refund") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isNotFound() }
                }
            }

            @Test
            fun `returns 422 when transaction is not in a valid state for refund`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.refundPayment(transactionId) } throws
                        InvalidTransactionStateException("Transaction is not in a valid state for refund")

                mockMvc.post("/api/payments/$transactionId/refund") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isUnprocessableContent() }
                }
            }

            @Test
            fun `returns 503 with Retry-After header on optimistic locking failure`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.refundPayment(transactionId) } throws
                        ObjectOptimisticLockingFailureException(Transaction::class.java, "test-id")

                mockMvc.post("/api/payments/$transactionId/refund") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isServiceUnavailable() }
                    header { string("Retry-After", "1") }
                }
            }
        }
    }

    @Nested
    inner class ReversePayment {

        private val validReverseRequest = ReversePaymentRequest(requesterIban = sourceIban.value)

        @Nested
        inner class HappyPath {

            @Test
            fun `returns 204 on successful reversal`() {

                val transactionId = UUID.randomUUID()
                val iban = validReverseRequest.toArgs().requesterIban
                every { paymentService.reversePayment(transactionId, iban) } just Runs

                mockMvc.post("/api/payments/$transactionId/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest)
                }.andExpect {
                    status { isNoContent() }
                }
            }
        }

        @Nested
        inner class Idempotency {

            @Test
            fun `returns 400 when Idempotency-Key header is missing`() {
                mockMvc.post("/api/payments/${UUID.randomUUID()}/reverse") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest)
                }.andExpect {
                    status { isBadRequest() }
                }
            }

            @Test
            fun `returns 204 on duplicate key without reprocessing`() {
                val transactionId = UUID.randomUUID()
                every { idempotencyService.exists(idempotencyKey) } returns true

                mockMvc.post("/api/payments/$transactionId/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest)
                }.andExpect {
                    status { isNoContent() }
                }

                verify(exactly = 0) { paymentService.reversePayment(any(), any()) }
            }
        }

        @Nested
        inner class FailureHandling {

            @Test
            fun `returns 400 when transaction ID is invalid`() {
                val invalidId = "invalid-id"

                mockMvc.post("/api/payments/$invalidId/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                }.andExpect {
                    status { isBadRequest() }
                }
            }

            @Test
            fun `returns 400 when requester IBAN is blank`() {
                mockMvc.post("/api/payments/${UUID.randomUUID()}/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest.copy(requesterIban = ""))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'requesterIban')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 400 when requester IBAN is invalid`() {
                mockMvc.post("/api/payments/${UUID.randomUUID()}/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest.copy(requesterIban = "INVALID"))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.fields[?(@.field == 'requesterIban')]") { isNotEmpty() }
                }
            }

            @Test
            fun `returns 403 when requester is not the source account owner`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.reversePayment(transactionId, sourceIban) } throws
                        UnauthorizedOperationException("Only the owner of the source account can request a reversal")

                mockMvc.post("/api/payments/$transactionId/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest)
                }.andExpect {
                    status { isForbidden() }
                }
            }

            @Test
            fun `returns 404 when transaction is not found`() {
                val unknownId = UUID.randomUUID()
                every { paymentService.reversePayment(unknownId, sourceIban) } throws
                        EntityNotFoundException("Transaction not found: $unknownId")

                mockMvc.post("/api/payments/$unknownId/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest)
                }.andExpect {
                    status { isNotFound() }
                }
            }

            @Test
            fun `returns 422 when transaction is not in a valid state for reversal`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.reversePayment(transactionId, sourceIban) } throws
                        InvalidTransactionStateException("Transaction is not in a valid state for reversal")

                mockMvc.post("/api/payments/$transactionId/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest)
                }.andExpect {
                    status { isUnprocessableContent() }
                }
            }

            @Test
            fun `returns 503 with Retry-After header on optimistic locking failure`() {
                val transactionId = UUID.randomUUID()
                every { paymentService.reversePayment(transactionId, sourceIban) } throws
                        ObjectOptimisticLockingFailureException(Transaction::class.java, "test-id")

                mockMvc.post("/api/payments/$transactionId/reverse") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validReverseRequest)
                }.andExpect {
                    status { isServiceUnavailable() }
                    header { string("Retry-After", "1") }
                }
            }
        }
    }
}
