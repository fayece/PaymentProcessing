package nl.fayece.paymentprocessing.domain

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import nl.fayece.paymentprocessing.domain.exceptions.InvalidIbanException
import kotlin.reflect.KClass

@JvmInline
value class Iban private constructor(val value: String) {

    // Mark a String for IBAN validation
    @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.RUNTIME)
    @Constraint(validatedBy = [Validator::class])
    annotation class Valid(
        val message: String = "Invalid IBAN",
        val groups: Array<KClass<*>> = [],
        val payload: Array<KClass<out Payload>> = []
    )

    // Validates marked String using the companion object
    class Validator : ConstraintValidator<Valid, String?> {
        override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
            // return true here to ensure that Jakarta annotations can handle null/blank cases separately
            if (value.isNullOrBlank()) return true
            return try {
                of(value)
                true
            } catch (e: InvalidIbanException) {
                context.disableDefaultConstraintViolation()
                context.buildConstraintViolationWithTemplate(e.message ?: "Invalid IBAN")
                    .addConstraintViolation()
                false
            }
        }
    }

    companion object {
        private val IBAN_REGEX = Regex("^NL\\d{2}[A-Z]{4}\\d{10}$")  // Dutch IBAN format only

        fun of(raw: String): Iban {
            val normalized = raw.uppercase().replace(" ", "")

            if (!normalized.matches(IBAN_REGEX)) throw InvalidIbanException(normalized, "Invalid IBAN format")
            if (!passesChecksum(normalized)) throw InvalidIbanException(normalized, "Invalid IBAN checksum")

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
