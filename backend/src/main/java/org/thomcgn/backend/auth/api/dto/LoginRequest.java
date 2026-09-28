package org.thomcgn.backend.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
