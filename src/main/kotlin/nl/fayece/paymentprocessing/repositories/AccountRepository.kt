package nl.fayece.paymentprocessing.repositories

import nl.fayece.paymentprocessing.domain.Account
import nl.fayece.paymentprocessing.domain.Iban
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByIban(iban: Iban): Optional<Account>
}
