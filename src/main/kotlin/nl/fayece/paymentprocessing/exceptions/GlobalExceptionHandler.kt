package nl.fayece.paymentprocessing.exceptions

import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import nl.fayece.paymentprocessing.domain.exceptions.InsufficientBalanceException
import nl.fayece.paymentprocessing.domain.exceptions.InvalidIbanException
import nl.fayece.paymentprocessing.domain.exceptions.InvalidTransactionStateException
import nl.fayece.paymentprocessing.domain.exceptions.UnauthorizedOperationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(InvalidTransactionStateException::class)
    fun handleInvalidTransactionState(ex: InvalidTransactionStateException, request: HttpServletRequest) =
        error(HttpStatus.UNPROCESSABLE_CONTENT, ex.message ?: "Invalid transaction state transition", request)

    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalance(ex: InsufficientBalanceException, request: HttpServletRequest) =
        error(HttpStatus.UNPROCESSABLE_CONTENT, ex.message ?: "Insufficient balance", request)

    @ExceptionHandler(InvalidIbanException::class)
    fun handleInvalidIban(ex: InvalidIbanException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid IBAN", request)

    @ExceptionHandler(UnauthorizedOperationException::class)
    fun handleUnauthorizedOperation(ex: UnauthorizedOperationException, request: HttpServletRequest) =
        error(HttpStatus.FORBIDDEN, ex.message ?: "You do not have permission to perform this operation", request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val fieldViolations = ex.bindingResult.fieldErrors.map { FieldViolation(it.field, it.defaultMessage ?: "Invalid value") }
        return error(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldViolations)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST, "Invalid type for request parameter", request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(ex: HttpMessageNotReadableException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", request)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException, request: HttpServletRequest) =
        error(HttpStatus.NOT_FOUND, "Resource not found", request)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request", request)

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(ex: EntityNotFoundException, request: HttpServletRequest) =
        error(HttpStatus.NOT_FOUND, ex.message ?: "Entity not found", request)

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException, request: HttpServletRequest) =
        error(HttpStatus.BAD_REQUEST, "Required header '${ex.headerName}' is missing", request)

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(ex: ObjectOptimisticLockingFailureException, request: HttpServletRequest) =
        error(HttpStatus.SERVICE_UNAVAILABLE, ex.message ?: "Service temporarily unavailable. Please try again later.", request)
            .also { it.headers.set("Retry-After", "1") }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest) {
        logger.error("Unhandled exception on ${request.method} ${request.requestURI}", ex)
        error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.", request)
//         error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.", request)
    }


    private fun error(status: HttpStatus, message: String, request: HttpServletRequest, fields: List<FieldViolation>? = null) =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI,
                fields = fields
            )
        )
}
