package nl.fayece.paymentprocessing.services

import tools.jackson.databind.ObjectMapper
import nl.fayece.paymentprocessing.domain.IdempotencyKey
import nl.fayece.paymentprocessing.repositories.IdempotencyKeyRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IdempotencyService (
    private val idemPotencyRepository: IdempotencyKeyRepository,
    private val objectMapper: ObjectMapper
) {
    fun <T: Any> resolve(key: String, transactionId: UUID, responseClass: Class<T>, block: () -> T): T {

        val existing = idemPotencyRepository.findByKey(key)

        if (existing.isPresent) {
            return objectMapper.readValue(existing.get().responseSnapshot, responseClass)
        }

        val result = block()
        idemPotencyRepository.save(
            IdempotencyKey(
                key = key,
                transactionId = transactionId,
                responseSnapshot = objectMapper.writeValueAsString(result)
            )
        )

        return result
    }

    fun record(key: String, transactionId: UUID) {
        idemPotencyRepository.save(
            IdempotencyKey(
                key = key,
                transactionId = transactionId,
                responseSnapshot = "{}"
            )
        )
    }

    fun exists(key: String): Boolean = idemPotencyRepository.findByKey(key).isPresent
}
