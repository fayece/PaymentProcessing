package nl.fayece.paymentprocessing.repository

import nl.fayece.paymentprocessing.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID>