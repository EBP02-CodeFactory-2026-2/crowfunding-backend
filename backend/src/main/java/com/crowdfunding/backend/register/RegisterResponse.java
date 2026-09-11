package com.crowdfunding.backend.register;

import java.time.LocalDateTime;

public record RegisterResponse(Long id, String fullName, String email, LocalDateTime createdAt) {

}
