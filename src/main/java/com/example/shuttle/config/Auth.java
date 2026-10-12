package com.example.shuttle.config;

import com.example.shuttle.domain.Role;
import com.example.shuttle.exception.ForbiddenException;
import com.example.shuttle.exception.UnauthorizedException;

/** Header-based identity (X-User, X-Role). The single seam to replace with JWT later. */
public record Auth(String userId, Role role) {

    public static Auth of(String user, String role) {
        if (user == null || user.isBlank() || role == null || role.isBlank()) {
            throw new UnauthorizedException("X-User and X-Role headers are required");
        }
        try {
            return new Auth(user.trim(), Role.valueOf(role.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Unknown role");
        }
    }

    public void require(Role... allowed) {
        for (Role r : allowed) {
            if (r == role) return;
        }
        throw new ForbiddenException("Role " + role + " is not allowed to do this");
    }
}
