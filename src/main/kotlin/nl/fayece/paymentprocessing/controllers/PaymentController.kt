package nl.fayece.paymentprocessing.controllers

import jakarta.validation.Valid
import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.dto.payments.PaymentRequest
import nl.fayece.paymentprocessing.dto.payments.PaymentResponse
import nl.fayece.paymentprocessing.services.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Currency

@RestController
@RequestMapping("/api/payments")
class PaymentController(private val paymentService: PaymentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submitPayment(@Valid @RequestBody request: PaymentRequest): PaymentResponse {
        val args = request.toArgs()
        val transaction = paymentService.submitPayment(
            sourceIban = args.sourceIban,
            destinationIban = args.destinationIban,
            amount = args.amount,
            currency = args.currency
        )

        return PaymentResponse.from(transaction)
    }
}
