package com.startocare.paymentservice.security;

import com.startocare.paymentservice.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.UUID;

@Service
public class PaymentJwtService {

    private final SecretKey signingKey;

    public PaymentJwtService(@Value("${app.jwt.secret}") String secret) {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public PaymentPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new PaymentPrincipal(
                UUID.fromString(claims.getSubject()),
                Role.valueOf(claims.get("role", String.class))
        );
    }
}
