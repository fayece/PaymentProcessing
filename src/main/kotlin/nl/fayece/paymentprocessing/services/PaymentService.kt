package nl.fayece.paymentprocessing.services

import jakarta.persistence.EntityNotFoundException
import nl.fayece.paymentprocessing.domain.Account
import nl.fayece.paymentprocessing.domain.Iban
import nl.fayece.paymentprocessing.domain.Transaction
import nl.fayece.paymentprocessing.domain.TransactionStatus
import nl.fayece.paymentprocessing.domain.TransactionStatusHistory
import nl.fayece.paymentprocessing.domain.exceptions.UnauthorizedOperationException
import nl.fayece.paymentprocessing.dto.audit.AuditEntryResponse
import nl.fayece.paymentprocessing.dto.audit.FailedPaymentResponse
import nl.fayece.paymentprocessing.repositories.AccountRepository
import nl.fayece.paymentprocessing.repositories.TransactionRepository
import nl.fayece.paymentprocessing.repositories.TransactionStatusHistoryRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.Currency
import java.util.UUID

@Service
class PaymentService (
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val statusHistoryRepository: TransactionStatusHistoryRepository,
    private val transactionHistoryWriter: TransactionHistoryWriter
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

        return createAndSettleTransaction(
            source, destination, amount, currency, SettlementMode.QUEUED
        )
    }

    @Transactional(readOnly = true)
    fun processQueuedPaymentIds(): List<UUID> =
        transactionRepository.findAllByStatus(TransactionStatus.QUEUED).map { it.id }

    @Transactional
    fun advanceQueuedPaymentToPending(transactionId: UUID) {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found: $transactionId") }

        transition(transaction, TransactionStatus.PENDING)
    }

    @Transactional
    fun handleSettlementConfirmation(transactionId: UUID) {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found: $transactionId") }

        transition(transaction, TransactionStatus.SETTLED)
    }

    @Retryable(
        includes = [ObjectOptimisticLockingFailureException::class],
        maxRetries = 3,
        delay = 50,
        multiplier = 2.0
    )
    @Transactional
    fun reversePayment(transactionId: UUID, requesterIban: Iban) {

        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found: $transactionId") }

        // TODO: Replace with proper authorization once security is implemented
        if (transaction.sourceAccount.iban != requesterIban) {
            throw UnauthorizedOperationException("Only the requester can reverse a payment")
        }

        val source = transaction.sourceAccount
        val destination = transaction.destinationAccount

        source.credit(transaction.amount)
        destination.debit(transaction.amount)
        accountRepository.saveAll(listOf(source, destination))

        transition(transaction, TransactionStatus.REVERSED)
    }

    @Retryable(
        includes = [ObjectOptimisticLockingFailureException::class],
        maxRetries = 3,
        delay = 50,
        multiplier = 2.0
    )
    @Transactional
    fun refundPayment(transactionId: UUID): Transaction {

        val original = transactionRepository.findById(transactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found: $transactionId") }

        transition(original, TransactionStatus.REFUNDED)

        return createAndSettleTransaction(
            original.destinationAccount,
            original.sourceAccount,
            original.amount,
            original.currency,
            SettlementMode.IMMEDIATE
        )
    }

    @Transactional(readOnly = true)
    fun getTransactionHistory(transactionId: UUID): List<AuditEntryResponse> {
        if (!transactionRepository.existsById(transactionId)) {
            throw EntityNotFoundException("Transaction not found: $transactionId")
        }
        return statusHistoryRepository.findAllByTransactionIdOrderByRecordedAtAsc(transactionId)
            .map { AuditEntryResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun getFailedPayments(): List<FailedPaymentResponse> =
        statusHistoryRepository.findAllByStatus(TransactionStatus.FAILED)
            .map { FailedPaymentResponse.from(it.transaction, it) }

    private fun transition(transaction: Transaction, status: TransactionStatus, reason: String? = null) {
        // Kept separate from recordHistory to avoid INITIATED from throwing an exception
        transaction.transitionTo(status)
        recordHistory(transaction, status, reason)
    }

    private fun recordHistory(transaction: Transaction, status: TransactionStatus, reason: String? = null) {
        statusHistoryRepository.save(TransactionStatusHistory(
            transaction = transaction,
            status = status,
            reason = reason)
        )
    }

    private fun createAndSettleTransaction(source: Account, destination: Account, amount: BigDecimal, currency: Currency, settlementMode: SettlementMode): Transaction {
        val transaction = transactionRepository.save(
            Transaction.create(source, destination, amount, currency)
        )
        recordHistory(transaction, TransactionStatus.INITIATED)

        try {
            transition(transaction, TransactionStatus.VALIDATED)

            source.debit(amount)
            destination.credit(amount)
            accountRepository.saveAll(listOf(source, destination))

            transition(transaction, TransactionStatus.QUEUED)

            if (settlementMode == SettlementMode.IMMEDIATE) {
                transition(transaction, TransactionStatus.PENDING)
                transition(transaction, TransactionStatus.SETTLED)
            }
        } catch (e: Exception) {
            if (e is ObjectOptimisticLockingFailureException) throw e

            transaction.transitionTo(TransactionStatus.FAILED)
            transactionHistoryWriter.recordFailure(transaction, e.message)
            throw e
        }

        return transactionRepository.save(transaction)
    }
}

enum class SettlementMode { QUEUED, IMMEDIATE }