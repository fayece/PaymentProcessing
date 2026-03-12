package nl.fayece.paymentprocessing.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "idempotency_keys")
class IdempotencyKey(

    @Id
    val key: String,

    val transactionId: UUID,

    val responseSnapshot: String,

    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun matches(otherKey: String): Boolean = key == otherKey
}
