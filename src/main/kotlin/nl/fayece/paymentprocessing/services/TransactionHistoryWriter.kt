package nl.fayece.paymentprocessing.services

import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.domain.TransactionStatusHistory
import nl.fayece.paymentprocessing.repositories.TransactionRepository
import nl.fayece.paymentprocessing.repositories.TransactionStatusHistoryRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionHistoryWriter(
    private val transactionRepository: TransactionRepository,
    private val statusHistoryRepository: TransactionStatusHistoryRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(transaction: Transaction, reason: String?) {
        transactionRepository.save(transaction)
        statusHistoryRepository.save(
            TransactionStatusHistory(
                transaction = transaction,
                status = TransactionStatus.FAILED,
                reason = reason
            )
        )
    }
}
