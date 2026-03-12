package nl.fayece.paymentprocessing.repositories

import nl.fayece.paymentprocessing.domain.FraudFlag
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FraudFlagRepository : JpaRepository<FraudFlag, UUID>