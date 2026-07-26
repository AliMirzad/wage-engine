package ir.manaz.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import ir.manaz.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                if (!jwtService.isAccessToken(claims)) {
                    log.debug("Rejecting non-access token on API endpoint");
                    chain.doFilter(request, response);
                    return;
                }

                String username = claims.getSubject();
                Long tenantId = claims.get(JwtService.CLAIM_TENANT_ID, Number.class).longValue();
                @SuppressWarnings("unchecked")
                List<String> auths = claims.get(JwtService.CLAIM_AUTHORITIES, List.class);
                Long userId = claims.get(JwtService.CLAIM_USER_ID, Number.class).longValue();

                var authorities = auths.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                Long effectiveTenantId = (tenantId == null || tenantId <= 0) ? null : tenantId;
                AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, effectiveTenantId, username);

                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                if (effectiveTenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
            } catch (ExpiredJwtException e) {
                request.setAttribute("auth.error.code", "auth.token.expired");
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute("auth.error.code", "auth.token.invalid");
            } catch (Exception e) {
                log.warn("Unexpected error parsing JWT", e);
                request.setAttribute("auth.error.code", "auth.token.invalid");
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }
}
