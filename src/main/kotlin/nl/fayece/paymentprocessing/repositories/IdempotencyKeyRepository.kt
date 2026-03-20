package nl.fayece.paymentprocessing.repositories

import nl.fayece.paymentprocessing.domain.IdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, String> {

    fun findByKey(key: String): Optional<IdempotencyKey>
}
