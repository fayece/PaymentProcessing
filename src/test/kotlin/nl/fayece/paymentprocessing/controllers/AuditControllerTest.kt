package nl.fayece.paymentprocessing.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.*
import jakarta.persistence.EntityNotFoundException
import nl.fayece.paymentprocessing.domain.Account
import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.dto.audit.AuditEntryResponse
import nl.fayece.paymentprocessing.dto.audit.FailedPaymentResponse
import nl.fayece.paymentprocessing.services.PaymentService
import nl.fayece.paymentprocessing.util.toMoney
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Currency
import java.util.UUID

@WebMvcTest(AuditController::class)
class AuditControllerTest {

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

    @Nested
    inner class GetPaymentHistory {

        @Test
        fun `returns 200 with payment history`() {

            val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur)
            every { paymentService.getTransactionHistory(transaction.id) } returns listOf(
                AuditEntryResponse(
                    status = TransactionStatus.INITIATED,
                    reason = "Payment created",
                    recordedAt = OffsetDateTime.parse("2026-03-19T22:52:00Z")
                    ),
                AuditEntryResponse(
                    status = TransactionStatus.VALIDATED,
                    reason = "Payment validated",
                    recordedAt = OffsetDateTime.parse("2026-03-19T22:52:01Z")
                )
            )

            mockMvc.get("/api/audit/payments/${transaction.id}/history")
                .andExpect {
                    status { isOk() }
                    jsonPath("$[0].status") { value("INITIATED") }
                    jsonPath("$[1].status") { value("VALIDATED") }
                    jsonPath("$[0].recordedAt") { exists() }
                }
        }

        @Test
        fun `returns 400 for invalid transaction ID`() {
            val invalidId = "invalid-id"

            mockMvc.get("/api/audit/payments/$invalidId/history")
                .andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 404 for non-existing transaction`() {
            val nonExistingId = UUID.randomUUID()

            every { paymentService.getTransactionHistory(nonExistingId) } throws
                    EntityNotFoundException("Transaction not found: $nonExistingId")

            mockMvc.get("/api/audit/payments/${nonExistingId}/history")
                .andExpect {
                    status { isNotFound() }
                }
        }
    }

    @Nested
    inner class GetFailedPayments {

        @Test
        fun `returns 200 with failed payments`() {

            every { paymentService.getFailedPayments() } returns listOf(
                FailedPaymentResponse(
                    transactionId = UUID.randomUUID(),
                    sourceIban = sourceIban.value,
                    destinationIban = destinationIban.value,
                    amount = BigDecimal("50.00").toMoney(),
                    currency = "EUR",
                    reason = "Insufficient funds",
                    failedAt = OffsetDateTime.parse("2026-03-19T22:52:00Z")
                )
            )

            mockMvc.get("/api/audit/payments/failed")
                .andExpect {
                    status { isOk() }
                    jsonPath("$[0].transactionId") { exists() }
                    jsonPath("$[0].reason") { value("Insufficient funds") }
                    jsonPath("$[0].failedAt") { exists() }
                }
        }

        @Test
        fun `returns 200 with empty list when no failed payments exist`() {
            every { paymentService.getFailedPayments() } returns emptyList()

            mockMvc.get("/api/audit/payments/failed")
                .andExpect {
                    status { isOk() }
                    jsonPath("$") { isArray() }
                    jsonPath("$") { isEmpty() }
                }
        }
    }
}
