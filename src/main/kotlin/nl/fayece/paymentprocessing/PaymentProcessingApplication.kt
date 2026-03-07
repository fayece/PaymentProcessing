package nl.fayece.paymentprocessing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PaymentProcessingApplication

fun main(args: Array<String>) {
    runApplication<PaymentProcessingApplication>(*args)
}
