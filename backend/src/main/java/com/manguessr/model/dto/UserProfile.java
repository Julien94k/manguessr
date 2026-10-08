package com.manguessr.model.dto;

import java.time.LocalDateTime;

public record UserProfile(Long id, String email, String username, String role, LocalDateTime createdAt) {}
