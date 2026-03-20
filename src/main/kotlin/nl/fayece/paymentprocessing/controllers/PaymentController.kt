package nl.fayece.paymentprocessing.controllers

import jakarta.validation.Valid
import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.dto.payments.PaymentRequest
import nl.fayece.paymentprocessing.dto.payments.PaymentResponse
import nl.fayece.paymentprocessing.dto.payments.ReversePaymentRequest
import nl.fayece.paymentprocessing.services.IdempotencyService
import nl.fayece.paymentprocessing.services.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Currency
import java.util.UUID

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val idempotencyService: IdempotencyService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submitPayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: PaymentRequest
    ): PaymentResponse {
        // Generate a unique transaction ID.
        // On idempotency conflict, this ID will be safely ignored.
        val transactionId = UUID.randomUUID()

        return idempotencyService.resolve(idempotencyKey, transactionId, PaymentResponse::class.java) {
            val args = request.toArgs()
            val transaction = paymentService.submitPayment(
                sourceIban = args.sourceIban,
                destinationIban = args.destinationIban,
                amount = args.amount,
                currency = args.currency
            )
            PaymentResponse.from(transaction)
        }
    }

    // Webhook, externally triggered when the settlement is confirmed by the system that handles it.
    @PostMapping("/{id}/confirm-settlement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmSettlement(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @PathVariable id: UUID
    ) {
        if (!idempotencyService.exists(idempotencyKey)) {
            paymentService.handleSettlementConfirmation(id)
            idempotencyService.record(idempotencyKey, id)
        }

    }

    @PostMapping("/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    fun refundPayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @PathVariable id: UUID
    ): PaymentResponse {

        return idempotencyService.resolve(idempotencyKey, id, PaymentResponse::class.java) {

            val transaction = paymentService.refundPayment(id)
            PaymentResponse.from(transaction)
        }
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reversePayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReversePaymentRequest
    ) {

        if (!idempotencyService.exists(idempotencyKey)) {
            val args = request.toArgs()
            paymentService.reversePayment(id, args.requesterIban)
            idempotencyService.record(idempotencyKey, id)
        }
    }
}
