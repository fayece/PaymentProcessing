package nl.fayece.paymentprocessing.repositories

import nl.fayece.paymentprocessing.domain.TransactionStatusHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TransactionStatusHistoryRepository : JpaRepository<TransactionStatusHistory, UUID>