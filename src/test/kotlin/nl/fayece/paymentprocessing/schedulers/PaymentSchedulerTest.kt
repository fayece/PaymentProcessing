package nl.fayece.paymentprocessing.schedulers

import io.mockk.*
import nl.fayece.paymentprocessing.services.PaymentService
import org.junit.jupiter.api.Test
import java.util.UUID

class PaymentSchedulerTest {

    private val paymentService: PaymentService = mockk();
    private val scheduler = PaymentScheduler(paymentService);

    @Test
    fun `processes all queued payment ids`() {
        val id1 = UUID.randomUUID();
        val id2 = UUID.randomUUID();
        every { paymentService.processQueuedPaymentIds() } returns listOf(id1, id2);
        every { paymentService.advanceQueuedPaymentToPending(any()) } just Runs;

        scheduler.processQueuedPayments();

        verify { paymentService.advanceQueuedPaymentToPending(id1) }
        verify { paymentService.advanceQueuedPaymentToPending(id2) }
    }

    @Test
    fun `continues processing remaining ids when one fails`() {
        val id1 = UUID.randomUUID();
        val id2 = UUID.randomUUID();
        every { paymentService.processQueuedPaymentIds() } returns listOf(id1, id2)
        every { paymentService.advanceQueuedPaymentToPending(id1) } throws Exception("Failed to process payment $id1")
        every { paymentService.advanceQueuedPaymentToPending(id2) } just Runs

        scheduler.processQueuedPayments()

        verify { paymentService.advanceQueuedPaymentToPending(id2) }
    }

    @Test
    fun `does nothing when there are no queued payments`() {
        every { paymentService.processQueuedPaymentIds() } returns emptyList()

        scheduler.processQueuedPayments()

        verify(exactly = 0) { paymentService.advanceQueuedPaymentToPending(any()) }
    }
}