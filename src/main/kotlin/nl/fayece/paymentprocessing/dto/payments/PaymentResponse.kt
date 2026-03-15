package nl.fayece.paymentprocessing.dto.payments

import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PaymentResponse(
    val transactionId: UUID,
    val sourceIban: String,
    val destinationIban: String,
    val amount: BigDecimal,
    val currency: String,
    val status: TransactionStatus,
    val createdAt: OffsetDateTime
) {
    companion object {
        fun from(transaction: Transaction) = PaymentResponse(
            transactionId = transaction.id,
            sourceIban = transaction.sourceAccount.iban.value,
            destinationIban = transaction.destinationAccount.iban.value,
            amount = transaction.amount,
            currency = transaction.currency.currencyCode,
            status = transaction.status,
            createdAt = transaction.createdAt
        )
    }
}
