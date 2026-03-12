package nl.fayece.paymentprocessing.domain

import java.math.BigInteger

@JvmInline
value class Iban(val value: String) {

    init {
        require(value.matches(IBAN_REGEX)) { "Invalid IBAN format: $value" }
        require(passesChecksum(value)) { "Invalid IBAN checksum: $value" }
    }

    override fun toString(): String = value

    companion object {
        private val IBAN_REGEX = Regex("^NL\\d{2}[A-Z]{4}\\d{10}$")  // Dutch IBAN format only

        private fun passesChecksum(iban: String): Boolean {

            // Check validation numbers (third and fourth position)
            val checkDigits = iban.substring(2, 4).toInt()
            if (checkDigits !in 2..98) return false

            // Rearrange the IBAN
            val rearranged = iban.drop(4) + iban.take(4)

            // Replace letters with their corresponding numbers
            val numeric = rearranged.map { char ->
                if (char.isLetter()) (char - 'A' + 10).toString() else char.toString()
            }.joinToString("")

            // Calculate modulo 97
            return numeric.toBigInteger().mod(97.toBigInteger()) == BigInteger.ONE
        }
    }
}
