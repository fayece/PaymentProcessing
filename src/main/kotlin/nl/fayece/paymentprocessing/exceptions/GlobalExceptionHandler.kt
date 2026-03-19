package nl.fayece.paymentprocessing.exceptions

import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import nl.fayece.paymentprocessing.domain.exceptions.InsufficientBalanceException
import nl.fayece.paymentprocessing.domain.exceptions.InvalidIbanException
import nl.fayece.paymentprocessing.domain.exceptions.InvalidTransactionStateException
import nl.fayece.paymentprocessing.domain.exceptions.UnauthorizedOperationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidTransactionStateException::class)
    fun handleInvalidTransactionState(
        ex: InvalidTransactionStateException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
            ErrorResponse(
                status = HttpStatus.UNPROCESSABLE_CONTENT.value(),
                error = HttpStatus.UNPROCESSABLE_CONTENT.reasonPhrase,
                message = ex.message ?: "Invalid transaction state transition",
                path = request.requestURI
            )
        )

    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalance(
        ex: InsufficientBalanceException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
            ErrorResponse(
                status = HttpStatus.UNPROCESSABLE_CONTENT.value(),
                error = HttpStatus.UNPROCESSABLE_CONTENT.reasonPhrase,
                message = ex.message ?: "Insufficient balance",
                path = request.requestURI
            )
        )

    @ExceptionHandler(InvalidIbanException::class)
    fun handleInvalidIban(
        ex: InvalidIbanException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = ex.message ?: "Invalid IBAN",
                path = request.requestURI
            )
        )

    @ExceptionHandler(UnauthorizedOperationException::class)
    fun handleUnauthorizedOperation(
        ex: UnauthorizedOperationException,
        request:HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(
                status = HttpStatus.FORBIDDEN.value(),
                error = HttpStatus.FORBIDDEN.reasonPhrase,
                message = ex.message ?: "You do not have permission to perform this operation",
                path = request.requestURI
            )
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val fieldViolations = ex.bindingResult.fieldErrors.map { error ->
            FieldViolation(
                field = error.field,
                message = error.defaultMessage ?: "Invalid value"
            )
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = "Validation failed",
                path = request.requestURI,
                fields = fieldViolations
            )
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
        ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Invalid type for request parameter",
            path = request.requestURI
        )
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = "Malformed or unreadable request body",
                path = request.requestURI
            )
        )

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = HttpStatus.NOT_FOUND.reasonPhrase,
                message = "Resource not found",
                path = request.requestURI
            )
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                message = ex.message ?: "Bad request",
                path = request.requestURI
            )
        )

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(
        ex: EntityNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                status = HttpStatus.NOT_FOUND.value(),
                error = HttpStatus.NOT_FOUND.reasonPhrase,
                message = ex.message ?: "Entity not found",
                path = request.requestURI
            )
        )

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(
        ex: ObjectOptimisticLockingFailureException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "1")
            .body(
                ErrorResponse(
                    status = HttpStatus.SERVICE_UNAVAILABLE.value(),
                    error = HttpStatus.SERVICE_UNAVAILABLE.reasonPhrase,
                    message = ex.message ?: "Service temporarily unavailable. Please try again later.",
                    path = request.requestURI
                )
            )

    @ExceptionHandler(Exception::class)
    fun handleException(
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
                message = "An unexpected error occurred. Please try again later.",
                path = request.requestURI
            )
        )
}
