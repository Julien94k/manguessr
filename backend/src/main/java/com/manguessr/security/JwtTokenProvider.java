package com.manguessr.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final Key key;
    private final long jwtExpirationInMs;

    public JwtTokenProvider(@Value("${jwt.secret}") String jwtSecret,
                            @Value("${jwt.expiration}") long jwtExpirationInMs) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    public String generateToken(Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return generateToken(principal);
    }

    public String generateToken(CustomUserDetails principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(principal.getUsername())
                .claim("role", principal.getUser().getRole())
                .claim("username", principal.getUser().getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            // Cas normal : un joueur revient apres expiration de sa session.
            log.debug("Token JWT expire");
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            // Toute JwtException, et pas une liste de sous-types : la SignatureException de jjwt
            // n'etait pas attrapee et remontait jusqu'au filtre, journalisee en ERROR avec sa
            // pile a chaque jeton forge. Un jeton refuse n'est pas une erreur du serveur.
            log.warn("Token JWT refuse : {}", ex.getClass().getSimpleName());
        }
        return false;
    }
}
