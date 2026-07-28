package ir.manaz.exception;

import ir.manaz.common.ErrorResponse;
import ir.manaz.config.MessageSourceConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    private String resolve(String key, Object[] args) {
        return messages.getMessage(key, args, key, MessageSourceConfig.FA);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessageKey(), ex.getArgs(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessageKey(), ex.getArgs(), req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessageKey(), ex.getArgs(), req);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessageKey(), ex.getArgs(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("؛ "));
        var msg = resolve("error.validation", null) + " (" + detail + ")";
        var body = ErrorResponse.of(400, "Bad Request", "error.validation", msg, req.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Query param با نوع اشتباه (مثلاً userId=abc یا from=notadate). قبلاً بدون handler
     * می‌رفت به generic و 500 می‌داد — الان 400 با پیام واضح.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        String requiredType = ex.getRequiredType() == null ? "?" : ex.getRequiredType().getSimpleName();
        String detail = ex.getName() + " باید از نوع " + requiredType + " باشد";
        var msg = resolve("error.validation", null) + " (" + detail + ")";
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", "error.validation", msg, req.getRequestURI()));
    }

    /**
     * Body غیرقابل parse (JSON خراب، فیلد اجباری null و ...). قبل از این handler،
     * چنین مواردی به generic می‌رفت و 500 می‌داد.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "error.bad_request", null, req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        String cause = ex.getMostSpecificCause().getMessage();
        String code = "error.data_integrity";
        if (cause != null) {
            if (cause.contains("uk_users_username_lower"))      code = "user.username.duplicate";
            else if (cause.contains("uk_users_email_lower"))    code = "user.email.duplicate";
            else if (cause.contains("uk_roles_name_system")
                    || cause.contains("uk_roles_name_tenanted"))  code = "role.name.duplicate";
        }
        return build(HttpStatus.CONFLICT, code, null, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "auth.forbidden", null, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "auth.unauthorized", null, req);
    }

//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
//        // TODO: log stack trace
//        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", null, req);
//    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        // Unknown failures were previously swallowed silently, which made every
        // 500 undiagnosable. The client still sees only the generic Persian
        // message; the detail stays server-side.
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", null, req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String key, Object[] args, HttpServletRequest req) {
        var body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                key,
                resolve(key, args),
                req.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}