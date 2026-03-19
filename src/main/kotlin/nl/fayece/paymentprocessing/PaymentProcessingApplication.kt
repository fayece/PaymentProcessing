package nl.fayece.paymentprocessing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

@EnableResilientMethods
@EnableScheduling
@SpringBootApplication
class PaymentProcessingApplication

fun main(args: Array<String>) {
    runApplication<PaymentProcessingApplication>(*args)
}
