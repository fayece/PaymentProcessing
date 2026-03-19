package nl.fayece.paymentprocessing.dto.payments

import jakarta.validation.constraints.NotBlank
import nl.fayece.paymentprocessing.domain.Iban

data class ReversePaymentRequest(
    @field:NotBlank(message = "Requester IBAN is required")
    @field:Iban.Valid
    val requesterIban: String?
) {
    fun toArgs() = ReversePaymentArgs(
        requesterIban = Iban.of(requesterIban!!)
    )
}

data class ReversePaymentArgs(
    val requesterIban: Iban
)
