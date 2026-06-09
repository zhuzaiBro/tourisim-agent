package com.tourism.rag.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Principal stored in SecurityContext after JWT validation. */
@Getter
@AllArgsConstructor
public class AuthUser {
    private final Long id;
    private final String username;
    private final String email;
    private final String role;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
