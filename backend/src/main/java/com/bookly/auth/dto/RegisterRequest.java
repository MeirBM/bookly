package com.bookly.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // Length is the requirement that actually resists guessing. A composition rule
        // ("one digit, one symbol") shrinks the search space more often than it grows it.
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 120) String fullName) {
}
