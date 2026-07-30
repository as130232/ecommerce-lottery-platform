package com.amway.ecommerce.lottery.auth;

/**
 * Lightweight principal placed in the SecurityContext after a JWT is validated.
 * Carries the user id so controllers don't have to re-load the user.
 */
public record AuthPrincipal(Long userId, String username, String role) {
}
