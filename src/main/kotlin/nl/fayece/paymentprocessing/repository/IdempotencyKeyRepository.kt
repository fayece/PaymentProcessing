package nl.fayece.paymentprocessing.repository

import nl.fayece.paymentprocessing.domain.IdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, String>