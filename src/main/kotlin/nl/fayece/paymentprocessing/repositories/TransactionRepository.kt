package nl.fayece.paymentprocessing.repositories

import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TransactionRepository : JpaRepository<Transaction, UUID> {
    fun findAllByStatus(status: TransactionStatus): List<Transaction>
}
