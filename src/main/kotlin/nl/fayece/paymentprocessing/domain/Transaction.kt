package nl.fayece.paymentprocessing.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import nl.fayece.paymentprocessing.util.toMoney

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Currency
import java.util.UUID

@Entity
@Table(name = "transactions")
class Transaction internal constructor(

    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    val sourceAccount: Account,

    @ManyToOne(fetch = FetchType.LAZY)
    val destinationAccount: Account,

    val amount: BigDecimal,

    val currency: Currency,

    @Enumerated(EnumType.STRING)
    var status: TransactionStatus = TransactionStatus.INITIATED,

    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Version
    var version: Long = 0
) {
    companion object {
        fun create(
            sourceAccount: Account,
            destinationAccount: Account,
            amount: BigDecimal,
            currency: Currency
        ): Transaction = Transaction(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = amount.toMoney(),
            currency = currency
        )
    }

    fun isTerminal(): Boolean = status == TransactionStatus.FAILED
            || status == TransactionStatus.REVERSED

    fun canTransitionTo(newStatus: TransactionStatus): Boolean {
        if (isTerminal()) return false
        return when (status) {
            TransactionStatus.INITIATED -> newStatus == TransactionStatus.VALIDATED || newStatus == TransactionStatus.FAILED
            TransactionStatus.VALIDATED -> newStatus == TransactionStatus.PENDING || newStatus == TransactionStatus.FAILED
            TransactionStatus.PENDING -> newStatus == TransactionStatus.SETTLED || newStatus == TransactionStatus.FAILED
            TransactionStatus.SETTLED -> newStatus == TransactionStatus.REVERSED
            else -> false
        }
    }

    fun transitionTo(newStatus: TransactionStatus) {
        require(canTransitionTo(newStatus)) {
            "Cannot transition from $status to $newStatus"
        }
        status = newStatus
        updatedAt = OffsetDateTime.now()
    }
}

