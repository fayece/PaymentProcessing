package nl.fayece.paymentprocessing.dto.payments

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import nl.fayece.paymentprocessing.domain.Iban
import java.math.BigDecimal
import java.util.Currency

@Suppress("NullableMustNotAnnotateNotNullMember", "JpaImmutableNotNullablePropertyInspection")
data class PaymentRequest(
    @field:NotBlank(message = "Source IBAN is required")
    @field:Iban.Valid
    val sourceIban: String?,

    @field:NotBlank(message = "Destination IBAN is required")
    @field:Iban.Valid
    val destinationIban: String?,

    @field:NotNull(message = "Amount is required")
    @field:Positive(message = "Amount must be positive")
    val amount: BigDecimal?,

    @field:NotBlank(message = "Currency is required")
    val currency: String?
) {
    fun toArgs() = SubmitPaymentArgs(
        sourceIban = Iban.of(sourceIban!!),
        destinationIban = Iban.of(destinationIban!!),
        amount = amount!!,
        currency = Currency.getInstance(currency!!)
    )
}

data class SubmitPaymentArgs(
    val sourceIban: Iban,
    val destinationIban: Iban,
    val amount: BigDecimal,
    val currency: Currency
)
