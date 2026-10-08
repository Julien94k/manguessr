package com.manguessr.service;

import com.manguessr.model.dto.AuthResponse;
import com.manguessr.model.dto.LoginRequest;
import com.manguessr.model.dto.RegisterRequest;
import com.manguessr.model.dto.UserProfile;
import com.manguessr.model.entity.User;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserProfile toProfile(User user);
}
