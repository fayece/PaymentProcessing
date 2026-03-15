package nl.fayece.paymentprocessing.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import nl.fayece.paymentprocessing.domain.exceptions.InsufficientBalanceException
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import nl.fayece.paymentprocessing.util.toMoney
import org.hibernate.annotations.NaturalId

@Entity
@Table(name = "accounts")
class Account(

    @Id
    val id: UUID = UUID.randomUUID(),

    val name: String,

    @NaturalId
    @Column(unique = true, nullable = false, updatable = false)
    val iban: Iban,

    var balance: BigDecimal = BigDecimal.ZERO.toMoney(),

    @Enumerated(EnumType.STRING)
    var status: AccountStatus = AccountStatus.ACTIVE,

    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Version
    var version: Long = 0
) {
    fun isActive(): Boolean = status == AccountStatus.ACTIVE
    fun createdBefore(other: OffsetDateTime): Boolean = createdAt.isBefore(other)
    fun createdAfter(other: OffsetDateTime): Boolean = createdAt.isAfter(other)

    fun debit(amount: BigDecimal) {
        val normalized = amount.toMoney()
        if (balance < normalized) throw InsufficientBalanceException(
            "Insufficient balance: available $balance, required $normalized"
        )
        balance -= normalized
    }

    fun credit(amount: BigDecimal) {
        balance += amount.toMoney()
    }
}
