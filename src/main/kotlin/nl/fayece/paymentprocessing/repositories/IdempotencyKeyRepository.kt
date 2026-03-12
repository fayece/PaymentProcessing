package nl.fayece.paymentprocessing.repositories

import nl.fayece.paymentprocessing.domain.IdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, String>