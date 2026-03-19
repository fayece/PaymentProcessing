package nl.fayece.paymentprocessing.schedulers

import nl.fayece.paymentprocessing.services.PaymentService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// Using a scheduler to process the queued payments
// TODO: Use a message broker like Kafka
// Message brokers are overkill in the current state, but marking as to-do for reference.
@Component
class PaymentScheduler(private val paymentService: PaymentService) {

    @Scheduled(fixedDelay = 5000)
    fun processQueuedPayments() {
        paymentService.processQueuedPaymentIds().forEach {
            try {
                paymentService.advanceQueuedPaymentToPending(it)
            } catch (e: Exception) {
                // Logging isn't implemented yet.
            }
        }
    }
}