package nl.fayece.paymentprocessing.controllers

import nl.fayece.paymentprocessing.dto.audit.AuditEntryResponse
import nl.fayece.paymentprocessing.services.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/audit")
class AuditController(private val paymentService: PaymentService) {

    @GetMapping("/payments/{id}/history")
    @ResponseStatus(HttpStatus.OK)
    fun getPaymentHistory(@PathVariable id: UUID): List<AuditEntryResponse> {
        return paymentService.getTransactionHistory(id)
    }
}