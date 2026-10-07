package dev.danyil.security;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.danyil.security.dtos.JwtAuthenticationDTO;
import dev.danyil.security.exceptions.JwtNoExistException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Component 
@RequiredArgsConstructor 
public class JwtService {

    @Value("${jwt-secret}")
    private String jwtSecret;

    @Value("${access-token-duration-minutes}")
    private int accessTokenDurationMinutes;

    @Value("${refresh-token-duration-days}")
    private int refreshTokenDurationDays;

    public JwtAuthenticationDTO generateAuthToken(String email, String role) {
        return JwtAuthenticationDTO.builder()
            .token(generateJwtToken(email, role))
            .refreshToken(generateRefreshToken(email, role))
            .build();
    }

    public JwtAuthenticationDTO refreshBaseToken(String email, String role, String refreshToken) {
        return JwtAuthenticationDTO.builder()
            .token(generateJwtToken(email, role))
            .refreshToken(refreshToken)
            .build();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSignKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        
        return claims.getSubject();
    }

    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSignKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String rolesString = claims.get("role", String.class);
        return List.of(rolesString.split(", "));
    }

    public boolean validateJwtToken(String token) {
        if (token == null || token.isBlank() || token.isEmpty()) {
            throw new JwtNoExistException("Token doesn't exist");
        }
        
        Jwts.parser()
            .verifyWith(getSignKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return true;
    }

    private String generateJwtToken(String email, String role) {
        Date date = Date.from(
            LocalDateTime.now()
            .plusMinutes(accessTokenDurationMinutes)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        );
        Date issuedAt = Date.from(
            LocalDateTime.now()
            .atZone(ZoneId.systemDefault())
            .toInstant()
        );

        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuedAt(issuedAt)
            .expiration(date)
            .signWith(getSignKey())
            .compact();
    }

    private String generateRefreshToken(String email, String role) {
        Date date = Date.from(
            LocalDateTime.now()
            .plusDays(refreshTokenDurationDays)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        );
        Date issuedAt = Date.from(
            LocalDateTime.now()
            .atZone(ZoneId.systemDefault())
            .toInstant()
        );

        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuedAt(issuedAt)
            .expiration(date)
            .signWith(getSignKey())
            .compact();
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

}
