package nl.fayece.paymentprocessing.domain

enum class TransactionStatus {
    INITIATED,
    VALIDATED,
    PENDING,
    SETTLED,
    FAILED,
    REVERSED,
    REFUNDED
}
