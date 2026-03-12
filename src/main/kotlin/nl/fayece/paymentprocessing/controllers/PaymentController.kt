package nl.fayece.paymentprocessing.controllers

import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.services.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Currency
import java.util.UUID

data class PaymentRequest(
    val sourceIban: String,
    val destinationIban: String,
    val amount: BigDecimal,
    val currency: String
)

data class PaymentResponse(
    val transactionId: UUID,
    val sourceIban: String,
    val destinationIban: String,
    val amount: BigDecimal,
    val currency: String,
    val status: TransactionStatus,
    val createdAt: OffsetDateTime
)

@RestController
@RequestMapping("/api/payments")
class PaymentController(private val paymentService: PaymentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submitPayment(@RequestBody request: PaymentRequest): PaymentResponse {
        val transaction = paymentService.submitPayment(
            sourceIban = Iban.of(request.sourceIban),
            destinationIban = Iban.of(request.destinationIban),
            amount = request.amount,
            currency = Currency.getInstance(request.currency)
        )
        return transaction.toResponse()
    }

    private fun Transaction.toResponse() = PaymentResponse(
        transactionId = id,
        sourceIban = sourceAccount.iban.value,
        destinationIban = destinationAccount.iban.value,
        amount = amount,
        currency = currency.currencyCode,
        status = status,
        createdAt = createdAt
    )
}