package nl.fayece.paymentprocessing.exceptions

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class FieldViolation(
    val field: String,
    val message: String
)

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val fields: List<FieldViolation>? = null
)
