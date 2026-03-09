package nl.fayece.paymentprocessing.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import nl.fayece.paymentprocessing.util.toMoney
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "accounts")
class Account(

    @Id
    val id: UUID = UUID.randomUUID(),

    val name: String,

    val iban: String,

    var balance: BigDecimal = BigDecimal.ZERO.toMoney(),

    @Enumerated(EnumType.STRING)
    var status: AccountStatus = AccountStatus.ACTIVE,

    val createdAt: Instant = Instant.now(),

    @Version
    var version: Long = 0
) {
    fun isActive(): Boolean = status == AccountStatus.ACTIVE
    fun createdBefore(other: Instant): Boolean = createdAt.isBefore(other)
    fun createdAfter(other: Instant): Boolean = createdAt.isAfter(other)

    fun debit(amount: BigDecimal) {
        val normalized = amount.toMoney()
        require(balance >= normalized) { "Insufficient balance" }
        balance -= normalized
    }

    fun credit(amount: BigDecimal) {
        balance += amount.toMoney()
    }
}
