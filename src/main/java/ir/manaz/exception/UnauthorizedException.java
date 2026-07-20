package ir.manaz.exception;

public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String messageKey, Object... args) {
        super(messageKey, args);
    }
}