package ir.manaz.security.jwt;

/**
 * Lightweight principal placed into SecurityContext after JWT validation.
 * Use SecurityContextHolder.getContext().getAuthentication().getPrincipal() to retrieve.
 */
public record AuthenticatedPrincipal(Long userId, Long tenantId, String username) {}
