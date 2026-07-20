package ir.manaz.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter writer;

    public JwtAuthEntryPoint(SecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        var code = (String) request.getAttribute("auth.error.code");
        if (code == null) code = "auth.unauthorized";
        writer.write(request, response, HttpStatus.UNAUTHORIZED, code);
    }
}