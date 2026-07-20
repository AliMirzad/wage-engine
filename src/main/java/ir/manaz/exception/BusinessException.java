package ir.manaz.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String messageKey;
    private final Object[] args;

    public BusinessException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

}
