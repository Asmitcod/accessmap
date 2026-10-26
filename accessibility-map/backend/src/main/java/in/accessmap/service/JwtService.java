package in.accessmap.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-ms:1800000}") long expirationMillis) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 random bytes.");
        }
        if (expirationMillis < 60_000 || expirationMillis > 86_400_000) {
            throw new IllegalArgumentException("JWT expiration must be between one minute and 24 hours.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String issue(String email) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    public String getEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }
}
