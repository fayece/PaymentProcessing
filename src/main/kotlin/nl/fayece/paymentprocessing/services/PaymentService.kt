package nl.fayece.paymentprocessing.services

import jakarta.persistence.EntityNotFoundException
import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.domain.TransactionStatusHistory
import nl.fayece.paymentprocessing.repositories.AccountRepository
import nl.fayece.paymentprocessing.repositories.TransactionRepository
import nl.fayece.paymentprocessing.repositories.TransactionStatusHistoryRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.Currency
import java.util.UUID

@Service
class PaymentService (
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val statusHistoryRepository: TransactionStatusHistoryRepository
) {
    @Retryable(
        includes = [ObjectOptimisticLockingFailureException::class],
        maxRetries = 3,
        delay = 50,
        multiplier = 2.0
    )

    @Transactional
    fun submitPayment(sourceIban: Iban, destinationIban: Iban, amount: BigDecimal, currency: Currency): Transaction {

        require(sourceIban != destinationIban) { "Source and destination accounts cannot be the same" }
        require(amount > BigDecimal.ZERO) { "Payment amount must be positive" }

        val source = accountRepository.findByIban(sourceIban)
            .orElseThrow { EntityNotFoundException("Account not found: $sourceIban") }
        val destination = accountRepository.findByIban(destinationIban)
            .orElseThrow { EntityNotFoundException("Account not found: $destinationIban") }

        require(source.isActive()) { "Source account is not active" }
        require(destination.isActive()) { "Destination account is not active" }

        val transaction = transactionRepository.save(
            Transaction.create(source, destination, amount, currency)
        )
        recordHistory(transaction, TransactionStatus.INITIATED)

        try {
            transaction.transitionTo(TransactionStatus.VALIDATED)
            recordHistory(transaction, TransactionStatus.VALIDATED)

            source.debit(amount)
            destination.credit(amount)
            accountRepository.saveAll(listOf(source, destination))

            transaction.transitionTo(TransactionStatus.PENDING)
            recordHistory(transaction, TransactionStatus.PENDING)

        } catch (e: Exception) {
            if (e is ObjectOptimisticLockingFailureException) throw e

            transaction.transitionTo(TransactionStatus.FAILED)
            recordHistory(transaction, TransactionStatus.FAILED, e.message)
            throw e
        }

        return transactionRepository.save(transaction)
    }

    @Transactional
    fun handleSettlementConfirmation(transactionId: UUID) {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found: $transactionId") }

        transaction.transitionTo(TransactionStatus.SETTLED)
        recordHistory(transaction, TransactionStatus.SETTLED)
    }

    private fun recordHistory(transaction: Transaction, status: TransactionStatus, reason: String? = null) {
        statusHistoryRepository.save(TransactionStatusHistory(
            transaction = transaction,
            status = status,
            reason = reason)
        )
    }
}