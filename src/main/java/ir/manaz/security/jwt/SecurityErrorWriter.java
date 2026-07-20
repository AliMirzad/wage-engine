package ir.manaz.security.jwt;

import ir.manaz.common.ErrorResponse;
import ir.manaz.config.MessageSourceConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityErrorWriter {

    private final MessageSource messages;
    private final ObjectMapper mapper;

    public SecurityErrorWriter(MessageSource messages, ObjectMapper mapper) {
        this.messages = messages;
        this.mapper = mapper;
    }

    public void write(HttpServletRequest req, HttpServletResponse res,
                      HttpStatus status, String code) throws IOException {
        var message = messages.getMessage(code, null, code, MessageSourceConfig.FA);
        var body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                req.getRequestURI()
        );
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(mapper.writeValueAsString(body));
    }
}
