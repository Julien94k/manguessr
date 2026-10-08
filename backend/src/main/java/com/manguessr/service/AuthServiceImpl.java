package com.manguessr.service;

import com.manguessr.model.dto.AuthResponse;
import com.manguessr.model.dto.LoginRequest;
import com.manguessr.model.dto.RegisterRequest;
import com.manguessr.model.dto.UserProfile;
import com.manguessr.model.entity.User;
import com.manguessr.repository.UserRepository;
import com.manguessr.security.CustomUserDetails;
import com.manguessr.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Inscription et connexion.
 *
 * Contrairement a revision-guide : aucune restriction de domaine email, aucun envoi de mail,
 * aucun jeton de verification. Le compte est utilisable immediatement et le JWT est renvoye
 * des l'inscription, pour que le joueur enchaine directement sur une partie.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    /**
     * Emails promus administrateur a l'inscription. Un admin peut declencher l'ingestion
     * du catalogue, operation longue et consommatrice de quota AniList.
     */
    private final Set<String> adminEmails;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtTokenProvider tokenProvider,
                           @Value("${app.admin-emails:}") String adminEmailList) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.adminEmails = Arrays.stream(adminEmailList.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String username = request.username().trim();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Cet email est déjà utilisé.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Ce pseudo est déjà pris.");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(adminEmails.contains(email) ? ADMIN_ROLE : DEFAULT_ROLE);

        User saved = userRepository.save(user);

        // On signe directement le token : pas d'etape de verification a franchir.
        String jwt = tokenProvider.generateToken(new CustomUserDetails(saved));
        return new AuthResponse(jwt, saved.getRole(), saved.getUsername());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        // Leve BadCredentialsException, traduite en 401 par le GlobalExceptionHandler.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        String jwt = tokenProvider.generateToken(principal);

        return new AuthResponse(jwt, principal.getUser().getRole(), principal.getUser().getUsername());
    }

    @Override
    public UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getUsername(),
                user.getRole(), user.getCreatedAt());
    }
}
