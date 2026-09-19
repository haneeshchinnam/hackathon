package com.example.detection.service;

import com.example.detection.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        extraClaims.put("roles", roles);
        extraClaims.put("type", "access");
        return generateToken(userDetails, extraClaims, jwtProperties.accessTokenExpiration());
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateToken(userDetails, new HashMap<>(Map.of("type", "refresh")), jwtProperties.refreshTokenExpiration());
    }

    private String generateToken(UserDetails userDetails, Map<String, Object> extraClaims, long expiration) {
        return Jwts.builder().claims(extraClaims).subject(userDetails.getUsername()).issuedAt(new Date(System.currentTimeMillis())).expiration(new Date(System.currentTimeMillis() + expiration)).signWith(getSigningKey()).compact();
    }

    private Claims extractAllClaims(String token) {
        // verifyWith(getSigningKey()) verifies HMAC signature against secret key
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public List<GrantedAuthority> extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);
        Object rolesObject = claims.get("roles");
        if (rolesObject instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public boolean hasType(String token, String type) {
        return type.equals(extractAllClaims(token).get("type", String.class));
    }

    public void validateToken(String token) throws JwtException, IllegalArgumentException {
        Claims claims = extractAllClaims(token);
        if (claims.getExpiration().before(new Date())) {
            throw new ExpiredJwtException(null, claims, "JWT token is expired");
        }
    }

    public boolean isValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.error("JWT validation error: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
