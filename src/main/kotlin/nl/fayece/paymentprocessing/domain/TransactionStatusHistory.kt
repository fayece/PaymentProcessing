package nl.fayece.paymentprocessing.domain

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "transaction_status_history")
class TransactionStatusHistory(

    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    val transaction: Transaction,

    @Enumerated(EnumType.STRING)
    val status: TransactionStatus,

    val reason: String? = null,

    var changedAt: OffsetDateTime = OffsetDateTime.now()
)
