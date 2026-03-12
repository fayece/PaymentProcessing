package nl.fayece.paymentprocessing.domain

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "fraud_flags")
class FraudFlag(

    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    val transaction: Transaction,

    val ruleName: String,

    val reason: String,

    val flaggedAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun describe(): String = "[$ruleName] $reason"
}
