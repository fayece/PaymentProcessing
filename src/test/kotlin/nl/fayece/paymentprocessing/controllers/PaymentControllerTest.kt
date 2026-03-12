package nl.fayece.paymentprocessing.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import jakarta.persistence.EntityNotFoundException
import nl.fayece.paymentprocessing.domain.*
import nl.fayece.paymentprocessing.services.PaymentService
import nl.fayece.paymentprocessing.util.toMoney
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

@WebMvcTest(PaymentController::class)
class PaymentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var paymentService: PaymentService

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    private val eur = Currency.getInstance("EUR")
    private val sourceIban = Iban.of("NL13TEST0123456789")
    private val destinationIban = Iban.of("NL65TEST0987656789")


    private val sourceAccount = Account(name = "Alice", iban = sourceIban, balance = BigDecimal("500.00").toMoney())
    private val destinationAccount = Account(name = "Bob", iban = destinationIban, balance = BigDecimal("100.00").toMoney())

    private val validRequest = PaymentRequest(
        sourceIban = sourceIban.value,
        destinationIban = destinationIban.value,
        amount = BigDecimal("100.00"),
        currency = eur.currencyCode
    )

    private fun pendingTransaction() = Transaction.create(sourceAccount, destinationAccount, BigDecimal("100.00"), eur).also {
        it.transitionTo(TransactionStatus.VALIDATED)
        it.transitionTo(TransactionStatus.PENDING)
    }

    @Nested
    inner class SubmitPayment {

        @Nested
        inner class HappyPath {

            @Test
            fun `returns 201 with transaction response`() {
                every { paymentService.submitPayment(any(), any(), any(), any())} returns pendingTransaction()

                mockMvc.post("/api/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.transactionId") { exists() }
                    jsonPath("$.sourceIban") { value(sourceIban.value) }
                    jsonPath("$.destinationIban") { value(destinationIban.value) }
                    jsonPath("$.amount") { value(100.00) }
                    jsonPath("$.currency") { value("EUR") }
                    jsonPath("$.status") { value("PENDING") }
                    jsonPath("$.createdAt") { exists() }
                }
            }
        }

        @Nested
        inner class Validation {

            @Test
            fun `returns 400 when source IBAN format is invalid`() {
                mockMvc.post("/api/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(sourceIban = "INVALID"))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { exists() }
                }
            }

            @Test
            fun `returns 400 when destination IBAN format is invalid`() {
                mockMvc.post("/api/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest.copy(destinationIban = "INVALID"))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { exists() }
                }
            }

            @Test
            fun `returns 400 when service rejects the request`() {
                every { paymentService.submitPayment(any(), any(), any(), any()) } throws
                        IllegalArgumentException("Payment amount must be positive")

                mockMvc.post("/api/payments") {
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
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(validRequest)
                }.andExpect {
                    status { isServiceUnavailable() }
                    header { string("Retry-After", "1") }
                }
            }
        }
    }
}