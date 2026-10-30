package com.startocare.userservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.userservice.dto.response.ApiError;
import com.startocare.userservice.entity.User;
import com.startocare.userservice.enums.Role;
import com.startocare.userservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if ((contextPath + "/api/auth/testing").equals(requestUri)
                || (contextPath + "/api/auth/send-otp").equals(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        UserPrincipal principal;
        try {
            String token = authorization.substring(7).trim();
            Jws<Claims> claims = jwtService.parseToken(token);
            String subject = claims.getPayload().getSubject();
            Object roleClaim = claims.getPayload().get("role");
            Object versionClaim = claims.getPayload().get("tokenVersion");
            if (subject == null || subject.isBlank()
                    || !(roleClaim instanceof String roleValue)
                    || !(versionClaim instanceof Number versionValue)
                    || versionValue.doubleValue() != versionValue.longValue()) {
                throw new IllegalArgumentException("Required JWT claims are missing");
            }
            UUID userId = UUID.fromString(subject);
            Role claimedRole = Role.valueOf(roleValue);
            Optional<User> userResult = userRepository.findById(userId);
            User user = userResult.orElseThrow(() -> new IllegalArgumentException("JWT user no longer exists"));
            if (!user.isEnabled() || user.getRole() != claimedRole
                    || user.getTokenVersion() != versionValue.longValue()) {
                throw new IllegalArgumentException("JWT is no longer valid");
            }
            principal = new UserPrincipal(userId, user.getRole());
        } catch (JwtException | IllegalArgumentException | ClassCastException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(request, response);
            return;
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "Invalid or expired bearer token",
                request.getRequestURI(),
                Map.of()
        );
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
