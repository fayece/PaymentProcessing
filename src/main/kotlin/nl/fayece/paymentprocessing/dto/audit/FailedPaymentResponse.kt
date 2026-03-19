package nl.fayece.paymentprocessing.dto.audit

import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatusHistory
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class FailedPaymentResponse(
    val transactionId: UUID,
    val sourceIban: String,
    val destinationIban: String,
    val amount: BigDecimal,
    val currency: String,
    val reason: String?,
    val failedAt: OffsetDateTime
) {
    companion object {
        fun from(transaction: Transaction, history: TransactionStatusHistory) = FailedPaymentResponse(
            transactionId = transaction.id,
            sourceIban = transaction.sourceAccount.iban.value,
            destinationIban = transaction.destinationAccount.iban.value,
            amount = transaction.amount,
            currency = transaction.currency.currencyCode,
            reason = history.reason,
            failedAt = history.recordedAt
        )
    }
}
