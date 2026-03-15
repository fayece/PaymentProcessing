package nl.fayece.paymentprocessing.dto.payments

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import nl.fayece.paymentprocessing.domain.Iban
import java.math.BigDecimal

data class PaymentRequest(
    @field:NotBlank(message = "Source IBAN is required")
    @field:Iban.Valid
    val sourceIban: String,

    @field:NotBlank(message = "Destination IBAN is required")
    @field:Iban.Valid
    val destinationIban: String,

    @field:NotBlank(message = "Amount is required")
    @field:Positive(message = "Amount must be positive")
    val amount: BigDecimal,

    @field:NotBlank(message = "Currency is required")
    val currency: String
)
