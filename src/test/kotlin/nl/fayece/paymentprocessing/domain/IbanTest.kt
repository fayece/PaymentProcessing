package nl.fayece.paymentprocessing.domain

import nl.fayece.paymentprocessing.domain.exceptions.InvalidIbanException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IbanTest {

    @Nested
    inner class Acceptance {
        @ParameterizedTest
        @ValueSource(strings = ["NL13TEST0123456789", "NL65TEST0987656789"])
        fun `valid Dutch IBAN is accepted`(raw: String) {
            assertEquals(raw, Iban.of(raw).value)
        }
    }

    @Nested
    inner class Normalization {
        @Test
        fun `IBAN with spaces is accepted and normalized`() {
            assertEquals("NL13TEST0123456789", Iban.of("NL13 TEST 0123 4567 89").value)
        }

        @Test
        fun `IBAN with lowercase letters is accepted and normalized`() {
            assertEquals("NL13TEST0123456789", Iban.of("nl13test0123456789").value)
        }
    }

    @Nested
    inner class Validation {
        @Test
        fun `IBAN with invalid format is rejected`() {
            assertThrows<InvalidIbanException> { Iban.of("INVALID") }
        }

        @Test
        fun `IBAN with invalid check digits is rejected`() {
            assertThrows<InvalidIbanException> { Iban.of("NL00TEST0123456789")}
        }

        @Test
        fun `IBAN with invalid checksum is rejected`() {
            assertThrows<InvalidIbanException> { Iban.of("NL13TEST0123456780") }
        }

        @Test
        fun `Non-Dutch IBAN is rejected`() {
            assertThrows<InvalidIbanException> { Iban.of("US12345678901234567890") }
        }
    }
}
