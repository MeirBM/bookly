package com.bookly.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // Length is the requirement that actually resists guessing. A composition rule
        // ("one digit, one symbol") shrinks the search space more often than it grows it.
        // Capped at 72 because BCrypt ignores every byte past 72 and says nothing: a user
        // choosing a 100-character passphrase would be authenticated by its first 72 bytes
        // while believing the rest protected them. Bytes, not characters - a non-Latin
        // passphrase reaches the limit in roughly half the characters.
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String fullName) {

    /**
     * A record's generated toString prints every component, password included, and Spring logs
     * handler arguments on several paths. That is enough to put a plaintext credential in a log
     * file that is then shipped somewhere less guarded than the database it came from.
     */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + ", password=****]";
    }
}
