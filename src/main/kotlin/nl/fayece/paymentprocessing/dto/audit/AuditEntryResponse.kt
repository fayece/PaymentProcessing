package nl.fayece.paymentprocessing.dto.audit

import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.domain.TransactionStatusHistory
import java.time.OffsetDateTime

data class AuditEntryResponse(
    val status: TransactionStatus,
    val reason: String?,
    val recordedAt: OffsetDateTime
) {
    companion object {
        fun from(history: TransactionStatusHistory) = AuditEntryResponse(
            status = history.status,
            reason = history.reason,
            recordedAt = history.recordedAt
        )
    }
}
