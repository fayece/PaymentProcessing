package nl.fayece.paymentprocessing.domain

@JvmInline
value class Iban private constructor(val value: String) {

    companion object {
        private val IBAN_REGEX = Regex("^NL\\d{2}[A-Z]{4}\\d{10}$")  // Dutch IBAN format only

        fun of(raw: String): Iban {
            val normalized = raw.uppercase().replace(" ", "")
            require(normalized.matches(IBAN_REGEX)) { "Invalid IBAN format: $normalized" }
            require(passesChecksum(normalized)) { "Invalid IBAN checksum: $normalized" }
            return Iban(normalized)
        }

        private fun passesChecksum(iban: String): Boolean {

            // Rearrange the IBAN
            val rearranged = iban.drop(4) + iban.take(4)

            // Replace letters with their corresponding numbers
            val numeric = buildString {
                for (char in rearranged) {
                    if (char.isLetter()) append(char - 'A' + 10)
                    else append(char)
                }
            }

            // Calculate modulo 97
            return mod97(numeric) == 1
        }

        private fun mod97(number: String): Int {
            var remainder = 0
            for (digit in number) {
                remainder = (remainder * 10 + (digit - '0')) % 97
            }

            return remainder
        }
    }

    override fun toString(): String = value
}
