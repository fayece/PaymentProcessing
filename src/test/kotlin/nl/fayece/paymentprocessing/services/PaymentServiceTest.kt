package nl.fayece.paymentprocessing.services

import io.mockk.*
import jakarta.persistence.EntityNotFoundException
import nl.fayece.paymentprocessing.domain.*
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

        paymentService = PaymentService(accountRepository, transactionRepository, statusHistoryRepository)

        sourceAccount = Account(name = "Alice", iban = sourceIban, balance = BigDecimal("500.00").toMoney())
        destinationAccount = Account(name = "Bob", iban = destinationIban, balance = BigDecimal("100.00").toMoney())

        every { accountRepository.findByIban(sourceIban) } returns Optional.of(sourceAccount)
        every { accountRepository.findByIban(destinationIban) } returns Optional.of(destinationAccount)
        every { accountRepository.saveAll(any<List<Account>>()) } returns listOf(sourceAccount, destinationAccount)
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { statusHistoryRepository.save(capture(capturedHistory)) } answers { firstArg() }
    }

    // submitPayment

    @Nested
    inner class SubmitPayment {

        @Nested
        inner class HappyPath {

            @Test
            fun `debits source account`() {
                paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("200.00"), eur)

                assertEquals(BigDecimal("300.00").toMoney(), sourceAccount.balance)
            }

            @Test
            fun `credits destination account`() {
                paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("200.00"), eur)

                assertEquals(BigDecimal("300.00").toMoney(), destinationAccount.balance)
            }

            @Test
            fun `returns transaction in PENDING state`() {
                val transaction = paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)

                assertEquals(TransactionStatus.PENDING, transaction.status)
            }

            @Test
            fun `records INITIATED, VALIDATED, PENDING history entries in order`() {
                paymentService.submitPayment(sourceIban, destinationIban, BigDecimal("50.00"), eur)

                assertEquals(
                    listOf(TransactionStatus.INITIATED, TransactionStatus.VALIDATED, TransactionStatus.PENDING),
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
                assertThrows<IllegalArgumentException> {
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
    inner class HandleSettlementConfirmation {

        @Nested
        inner class HappyPath {

            @Test
            fun `transitions transaction to SETTLED`() {
                val transaction = Transaction.create(sourceAccount, destinationAccount, BigDecimal("50.00"), eur).also {
                    it.transitionTo(TransactionStatus.VALIDATED)
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
                    it.transitionTo(TransactionStatus.PENDING)
                }
                every { transactionRepository.findById(transaction.id) } returns Optional.of(transaction)

                paymentService.handleSettlementConfirmation(transaction.id)

                assertTrue(capturedHistory.any { it.status == TransactionStatus.SETTLED })
            }
        }

        @Nested
        inner class FailureHandling {

            @Test
            fun `throws EntityNotFoundException for unknown transaction`() {
                val unknownId = UUID.randomUUID()
                every { transactionRepository.findById(unknownId) } returns Optional.empty()

                assertThrows<EntityNotFoundException> {
                    paymentService.handleSettlementConfirmation(unknownId)
                }
            }
        }
    }
}
