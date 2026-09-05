package com.bookly.auth.dto;

import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName) {
}
