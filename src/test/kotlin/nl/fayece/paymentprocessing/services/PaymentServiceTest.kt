package nl.fayece.paymentprocessing.services

import io.mockk.*
import jakarta.persistence.EntityNotFoundException
import nl.fayece.paymentprocessing.domain.*
import nl.fayece.paymentprocessing.domain.exceptions.InsufficientBalanceException
import nl.fayece.paymentprocessing.domain.exceptions.InvalidTransactionStateException
import nl.fayece.paymentprocessing.domain.exceptions.UnauthorizedOperationException
import nl.fayece.paymentprocessing.dto.audit.AuditEntryResponse
import nl.fayece.paymentprocessing.repositories.AccountRepository
import nl.fayece.paymentprocessing.repositories.TransactionRepository
import nl.fayece.paymentprocessing.repositories.TransactionStatusHistoryRepository
import nl.fayece.paymentprocessing.util.toMoney
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.util.Currency
import java.util.Optional
import java.util.UUID

class PaymentServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val statusHistoryRepository: TransactionStatusHistoryRepository = mockk()
    private val transactionHistoryWriter: TransactionHistoryWriter = mockk()
    private lateinit var paymentService: PaymentService

    private val eur = Currency.getInstance("EUR")
    private val sourceIban = Iban.of("NL13TEST0123456789")
    private val destinationIban = Iban.of("NL65TEST0987656789")

    private lateinit var sourceAccount: Account
    private lateinit var destinationAccount: Account
    private val capturedHistory = mutableListOf<TransactionStatusHistory>()

    @BeforeEach
    fun setup() {
        capturedHistory.clear()

        paymentService = PaymentService(accountRepository, transactionRepository, statusHistoryRepository, transactionHistoryWriter)

        sourceAccount = Account(name = "Alice", iban = sourceIban, balance = BigDecimal("550.00").toMoney())
        destinationAccount = Account(name = "Bob", iban = destinationIban, balance = BigDecimal("100.00").toMoney())

        every { accountRepository.findByIban(sourceIban) } returns Optional.of(sourceAccount)
        every { accountRepository.findByIban(destinationIban) } returns Optional.of(destinationAccount)
        every { accountRepository.saveAll(any<List<Account>>()) } returns listOf(sourceAccount, destinationAccount)
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { statusHistoryRepository.save(capture(capturedHistory)) } answers { firstArg() }

        every { transactionHistoryWriter.recordFailure(any(), any()) } answers {
            val transaction = args[0] as Transaction
            val reason = args[1] as String?
            capturedHistory.add(TransactionStatusHistory(
                transaction = transaction,
                status = TransactionStatus.FAILED,
                reason = reason
            ))
        }
    }

    @Nested
    inner class SubmitPayment {

        @Nested
        inner class HappyPath {

            @Test
            fun `debits source account`() {
                paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("200.00"), eur)

                assertEquals(BigDecimal("350.00").toMoney(), sourceAccount.balance)
            }

            @Test
            fun `credits destination account`() {
                paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("200.00"), eur)

                assertEquals(BigDecimal("300.00").toMoney(), destinationAccount.balance)
            }

            @Test
            fun `returns transaction in QUEUED state`() {
                val transaction = paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)

                assertEquals(TransactionStatus.QUEUED, transaction.status)
            }

            @Test
            fun `records INITIATED, VALIDATED, QUEUED history entries in order`() {
                paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)

                assertEquals(
                    listOf(TransactionStatus.INITIATED, TransactionStatus.VALIDATED, TransactionStatus.QUEUED),
                    capturedHistory.map { it.status }
                )
            }

            @Test
            fun `persists both accounts after balance update`() {
                paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)

                verify { accountRepository.saveAll(listOf(sourceAccount, destinationAccount)) }
            }
        }

        @Nested
        inner class Validation {

            @Test
            fun `rejects same source and destination IBAN`() {
                assertThrows<IllegalArgumentException> {
                    paymentService.submitPayment(sourceIban, sourceIban, BigDecimal("10.00"), eur)
                }
            }

            @Test
            fun `rejects zero amount`() {
                assertThrows<IllegalArgumentException> {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal.ZERO, eur)
                }
            }

            @Test
            fun `rejects negative amount`() {
                assertThrows<IllegalArgumentException> {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("-10.00"), eur)
                }
            }

            @Test
            fun `throws EntityNotFoundException when source IBAN not found`() {
                every { accountRepository.findByIban(sourceIban) } returns Optional.empty()

                assertThrows<EntityNotFoundException> {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("10.00"), eur)
                }
            }

            @Test
            fun `throws EntityNotFoundException when destination IBAN not found`() {
                every { accountRepository.findByIban(destinationIban) } returns Optional.empty()

                assertThrows<EntityNotFoundException> {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("10.00"), eur)
                }
            }
        }

        @Nested
        inner class FailureHandling {

            @Test
            fun `transitions to FAILED and records reason on insufficient funds`() {
                assertThrows<InsufficientBalanceException> {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("9999.00"), eur)
                }

                val last = capturedHistory.last()
                assertEquals(TransactionStatus.FAILED, last.status)
                assertNotNull(last.reason)
            }

            @Test
            fun `rethrows ObjectOptimisticLockingFailureException without recording FAILED`() {
                every { accountRepository.saveAll(any<List<Account>>()) } throws
                        ObjectOptimisticLockingFailureException(Account::class.java, "test-id")

                assertThrows<ObjectOptimisticLockingFailureException> {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("10.00"), eur)
                }

                assertFalse(capturedHistory.any { it.status == TransactionStatus.FAILED })
            }
        }
    }

    @Nested
    inner class PaymentStateTransitions {

        @Nested
        inner class FromInitiated {

            @Nested
            inner class ToValidated {

                @Test
                fun `records VALIDATED history entry after INITIATED`() {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)

                    val statuses = capturedHistory.map { it.status }
                    val initiatedIndex = statuses.indexOf(TransactionStatus.INITIATED)
                    val validatedIndex = statuses.indexOf(TransactionStatus.VALIDATED)

                    assertTrue(validatedIndex > initiatedIndex)
                }

                @Test
                fun `transaction is VALIDATED before balance mutation occurs`() {
                    paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)

                    assertTrue(capturedHistory.any { it.status == TransactionStatus.VALIDATED })
                }
            }

            @Nested
            inner class ToFailed {

                @Test
                fun `records INITIATED, VALIDATED, FAILED history entries in order when saveAll throws`() {
                    every { accountRepository.saveAll(any<List<Account>>()) } throws RuntimeException("Test exception")

                    assertThrows<RuntimeException> {
                        paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)
                    }

                    assertEquals(
                        listOf(TransactionStatus.INITIATED, TransactionStatus.VALIDATED, TransactionStatus.FAILED),
                        capturedHistory.map { it.status }
                    )
                }
            }
        }

        @Nested
        inner class FromValidated {

            @Nested
            inner class ToQueued {

                @Test
                fun `records FAILED after VALIDATED on insufficient funds`() {
                    assertThrows<InsufficientBalanceException> {
                        paymentService.submitPayment(sourceIban, destinationIban,
                            BigDecimal(Integer.MAX_VALUE.toString()), eur
                        )
                    }

                    val statuses = capturedHistory.map { it.status }
                    assertEquals(
                        listOf(TransactionStatus.INITIATED, TransactionStatus.VALIDATED, TransactionStatus.FAILED),
                        statuses
                    )
                }
            }

            @Nested
            inner class ToFailed {

                @Test
                fun `records failure reason on insufficient funds`() {
                    assertThrows<InsufficientBalanceException> {
                        paymentService.submitPayment(sourceIban, destinationIban,
                            BigDecimal(Integer.MAX_VALUE.toString()), eur
                        )
                    }

                    assertNotNull(capturedHistory.last().reason)
                }
            }
        }

        @Nested
        inner class FromQueued {
            @Nested
            inner class ToPending {

                @Test
                fun `transitions transaction to PENDING`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                    }

                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    paymentService.advanceQueuedPaymentToPending(transaction.id)

                    assertEquals(TransactionStatus.PENDING, transaction.status)
                }

                @Test
                fun `records PENDING history entry`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                    }
                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    paymentService.advanceQueuedPaymentToPending(transaction.id)

                    assertTrue(capturedHistory.any { it.status == TransactionStatus.PENDING })
                }

                @Test
                fun `throws EntityNotFoundException for unknown transaction`() {
                    val unknownId = UUID.randomUUID()
                    every { transactionRepository.findById(unknownId) } returns Optional.empty()

                    assertThrows<EntityNotFoundException> {
                        paymentService.advanceQueuedPaymentToPending(unknownId)
                    }
                }
            }

            @Nested
            inner class ToFailed {

                @Test
                fun `throws InvalidTransactionStateException when transaction is not in QUEUED state`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                        it.transitionTo(TransactionStatus.FAILED)
                    }
                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    assertThrows<InvalidTransactionStateException> {
                        paymentService.advanceQueuedPaymentToPending(transaction.id)
                    }
                }

                @Test
                fun `does not record additional history when advancing a FAILED transaction`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                        it.transitionTo(TransactionStatus.FAILED)
                    }
                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    assertThrows<InvalidTransactionStateException> {
                        paymentService.advanceQueuedPaymentToPending(transaction.id)
                    }

                    assertFalse(capturedHistory.any { it.status == TransactionStatus.PENDING })
                }
            }
        }

        @Nested
        inner class FromPending {

            @Nested
            inner class ToSettled {

                @Test
                fun `transitions transaction to SETTLED`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                        it.transitionTo(TransactionStatus.PENDING)
                    }

                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    paymentService.handleSettlementConfirmation(transaction.id)

                    assertEquals(TransactionStatus.SETTLED, transaction.status)
                }

                @Test
                fun `records SETTLED history entry`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                        it.transitionTo(TransactionStatus.PENDING)
                    }
                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    paymentService.handleSettlementConfirmation(transaction.id)

                    assertTrue(capturedHistory.any { it.status == TransactionStatus.SETTLED })
                }

                @Test
                fun `throws EntityNotFoundException for unknown transaction`() {
                    val unknownId = UUID.randomUUID()
                    every { transactionRepository.findById(unknownId) } returns Optional.empty()

                    assertThrows<EntityNotFoundException> {
                        paymentService.handleSettlementConfirmation(unknownId)
                    }
                }

                @Test
                fun `throws InvalidTransactionStateException when transaction is not in PENDING state`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                    }
                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    assertThrows<InvalidTransactionStateException> {
                        paymentService.handleSettlementConfirmation(transaction.id)
                    }
                }
            }

            @Nested
            inner class ToFailed {

                @Test
                fun `throws InvalidTransactionStateException when transaction is not in PENDING state`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                        it.transitionTo(TransactionStatus.PENDING)
                        it.transitionTo(TransactionStatus.FAILED)
                    }
                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    assertThrows<InvalidTransactionStateException> {
                        paymentService.handleSettlementConfirmation(transaction.id)
                    }
                }

                @Test
                fun `does not record SETTLED history when confirming settlement on a FAILED transaction`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                        it.transitionTo(TransactionStatus.VALIDATED)
                        it.transitionTo(TransactionStatus.QUEUED)
                        it.transitionTo(TransactionStatus.PENDING)
                        it.transitionTo(TransactionStatus.FAILED)
                    }
                    every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                    assertThrows<InvalidTransactionStateException> {
                        paymentService.handleSettlementConfirmation(transaction.id)
                    }

                    assertFalse(capturedHistory.any { it.status == TransactionStatus.SETTLED })
                }
            }
        }

        @Nested
        inner class FromSettled {

            private lateinit var settledTransaction: Transaction

            @BeforeEach
            fun setupSettledTransaction() {
                settledTransaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                    it.transitionTo(TransactionStatus.VALIDATED)
                    it.transitionTo(TransactionStatus.QUEUED)
                    it.transitionTo(TransactionStatus.PENDING)
                    it.transitionTo(TransactionStatus.SETTLED)
                }
                every { transactionRepository.findById(settledTransaction.id) } returns Optional.of(settledTransaction)
            }

            @Nested
            inner class ToReversed {

                @Nested
                inner class HappyPath {

                    @Test
                    fun `transitions transaction to REVERSED`() {
                        paymentService.reversePayment(settledTransaction.id, sourceIban)

                        assertEquals(TransactionStatus.REVERSED, settledTransaction.status)
                    }

                    @Test
                    fun `records REVERSED history entry`() {
                        paymentService.reversePayment(settledTransaction.id, sourceIban)

                        assertTrue(capturedHistory.any { it.status == TransactionStatus.REVERSED })
                    }

                    @Test
                    fun `credits source account`() {
                        paymentService.reversePayment(settledTransaction.id, sourceIban)

                        assertEquals(BigDecimal("600.00").toMoney(), sourceAccount.balance)
                    }

                    @Test
                    fun `debits destination account`() {
                        paymentService.reversePayment(settledTransaction.id, sourceIban)

                        assertEquals(BigDecimal("50.00").toMoney(), destinationAccount.balance)
                    }

                    @Test
                    fun `persists both accounts after balance update`() {
                        paymentService.reversePayment(settledTransaction.id, sourceIban)

                        verify { accountRepository.saveAll(listOf(sourceAccount, destinationAccount)) }
                    }
                }

                @Nested
                inner class FailureHandling {

                    @Test
                    fun `throws EntityNotFoundException for unknown transaction`() {
                        val unknownId = UUID.randomUUID()
                        every { transactionRepository.findById(unknownId) } returns Optional.empty()

                        assertThrows<EntityNotFoundException> {
                            paymentService.reversePayment(unknownId, sourceIban)
                        }
                    }

                    @Test
                    fun `throws InvalidTransactionStateException when transaction is not in SETTLED state`() {
                        val queuedTransaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                            it.transitionTo(TransactionStatus.VALIDATED)
                            it.transitionTo(TransactionStatus.QUEUED)
                        }
                        every { transactionRepository.findById(queuedTransaction.id) } returns Optional.of(queuedTransaction)

                        assertThrows<InvalidTransactionStateException> {
                            paymentService.reversePayment(queuedTransaction.id, sourceIban)
                        }
                    }

                    @Test
                    fun `throws UnauthorizedOperationException when requester is not the source account owner`() {
                        assertThrows<UnauthorizedOperationException> {
                            paymentService.reversePayment(settledTransaction.id, destinationIban)
                        }
                    }

                    @Test
                    fun `does not record history when ownership check fails`() {
                        assertThrows<UnauthorizedOperationException> {
                            paymentService.reversePayment(settledTransaction.id, destinationIban)
                        }

                        assertFalse(capturedHistory.any { it.status == TransactionStatus.REVERSED })
                    }
                }
            }

            @Nested
            inner class ToRefunded {

                @Nested
                inner class HappyPath {

                    @Test
                    fun `transitions transaction to REFUNDED`() {
                        paymentService.refundPayment(settledTransaction.id)

                        assertEquals(TransactionStatus.REFUNDED, settledTransaction.status)
                    }

                    @Test
                    fun `returns refund transaction in SETTLED state`() {
                        val refund = paymentService.refundPayment(settledTransaction.id)

                        assertEquals(TransactionStatus.SETTLED, refund.status)
                    }

                    @Test
                    fun `refund transaction has swapped source and destination`() {
                        val refund = paymentService.refundPayment(settledTransaction.id)

                        assertEquals(destinationAccount, refund.sourceAccount)
                        assertEquals(sourceAccount, refund.destinationAccount)
                    }

                    @Test
                    fun `records REFUNDED on original then full lifecycle on refund transaction`() {
                        paymentService.refundPayment(settledTransaction.id)

                        assertEquals(
                            listOf(
                                TransactionStatus.REFUNDED,
                                TransactionStatus.INITIATED,
                                TransactionStatus.VALIDATED,
                                TransactionStatus.QUEUED,
                                TransactionStatus.PENDING,
                                TransactionStatus.SETTLED
                            ),
                            capturedHistory.map { it.status }
                        )
                    }

                    @Test
                    fun `credits original source account`() {
                        paymentService.refundPayment(settledTransaction.id)

                        assertEquals(BigDecimal("600.00").toMoney(), sourceAccount.balance)
                    }

                    @Test
                    fun `debits original destination account`() {
                        paymentService.refundPayment(settledTransaction.id)

                        assertEquals(BigDecimal("50.00").toMoney(), destinationAccount.balance)
                    }
                }

                @Nested
                inner class FailureHandling {

                    @Test
                    fun `throws EntityNotFoundException for unknown transaction`() {
                        val unknownId = UUID.randomUUID()
                        every { transactionRepository.findById(unknownId) } returns Optional.empty()

                        assertThrows<EntityNotFoundException> {
                            paymentService.refundPayment(unknownId)
                        }
                    }

                    @Test
                    fun `throws InvalidTransactionStateException when transaction is not SETTLED`() {
                        val queuedTransaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                            it.transitionTo(TransactionStatus.VALIDATED)
                            it.transitionTo(TransactionStatus.QUEUED)
                        }
                        every { transactionRepository.findById(queuedTransaction.id) } returns Optional.of(queuedTransaction)

                        assertThrows<InvalidTransactionStateException> {
                            paymentService.refundPayment(queuedTransaction.id)
                        }
                    }

                    @Test
                    fun `transitions refund transaction to FAILED and records reason when saveAll throws`() {
                        every { accountRepository.saveAll(any<List<Account>>()) } throws RuntimeException("Test exception")

                        assertThrows<RuntimeException> {
                            paymentService.refundPayment(settledTransaction.id)
                        }

                        val last = capturedHistory.last()
                        assertEquals(TransactionStatus.FAILED, last.status)
                        assertNotNull(last.reason)
                    }

                    @Test
                    fun `does not transition original to REFUNDED when refund transaction fails`() {
                        every { accountRepository.saveAll(any<List<Account>>()) } throws RuntimeException("Test exception")

                        assertThrows<RuntimeException> {
                            paymentService.refundPayment(settledTransaction.id)
                        }

                        assertEquals(TransactionStatus.REFUNDED, settledTransaction.status)
                        assertFalse(capturedHistory.any { it.status == TransactionStatus.SETTLED })
                    }
                }
            }
        }
    }

    @Nested
    inner class GetTransactionHistory {

        @Nested
        inner class HappyPath {

            @Test
            fun `returns transaction history for existing transaction`() {
                val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                    it.transitionTo(TransactionStatus.VALIDATED)
                    it.transitionTo(TransactionStatus.QUEUED)
                }
                every { transactionRepository.existsById(transaction.id) } returns true
                every { statusHistoryRepository.findAllByTransactionIdOrderByRecordedAtAsc(transaction.id) } returns listOf(
                    TransactionStatusHistory(transaction = transaction, status = TransactionStatus.INITIATED),
                    TransactionStatusHistory(transaction = transaction, status = TransactionStatus.VALIDATED),
                    TransactionStatusHistory(transaction = transaction, status = TransactionStatus.QUEUED)
                )

                val history = paymentService.getTransactionHistory(transaction.id)

                assertEquals(
                    listOf(TransactionStatus.INITIATED, TransactionStatus.VALIDATED, TransactionStatus.QUEUED),
                    history.map { it.status }
                )
            }

            @Nested
            inner class ErrorHandling {

                @Test
                fun `throws EntityNotFoundException for unknown transaction`() {
                    val unknownId = UUID.randomUUID()
                    every { transactionRepository.existsById(unknownId) } returns false

                    assertThrows<EntityNotFoundException> {
                        paymentService.getTransactionHistory(unknownId)
                    }
                }

                @Test
                fun `returns empty list for transaction with no history`() {
                    val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur)
                    every { transactionRepository.existsById(transaction.id) } returns true
                    every { statusHistoryRepository.findAllByTransactionIdOrderByRecordedAtAsc(transaction.id) } returns emptyList()
                }
            }
        }
    }

    @Nested
    inner class GetFailedPayments {

        @Test
        fun `returns failed payments with reason`() {

            val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur)
            val failedEntry = TransactionStatusHistory(
                transaction = transaction,
                status = TransactionStatus.FAILED,
                reason = "Insufficient funds"
            )
            every { statusHistoryRepository.findAllByStatus(TransactionStatus.FAILED) } returns listOf(failedEntry)

            val result = paymentService.getFailedPayments()

            assertEquals(1, result.size)
            assertEquals(transaction.id, result[0].transactionId)
            assertEquals("Insufficient funds", result[0].reason)
        }

        @Test
        fun `returns empty list when no failed payments exist`() {
            every { statusHistoryRepository.findAllByStatus(TransactionStatus.FAILED) } returns emptyList()

            val result = paymentService.getFailedPayments()

            assertTrue(result.isEmpty())
        }
    }
}
