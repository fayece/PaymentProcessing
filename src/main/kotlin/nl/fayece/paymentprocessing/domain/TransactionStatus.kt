package nl.fayece.paymentprocessing.domain

enum class TransactionStatus {
    INITIATED,
    VALIDATED,
    QUEUED,
    PENDING,
    SETTLED,
    FAILED,
    REVERSED,
    REFUNDED
}
