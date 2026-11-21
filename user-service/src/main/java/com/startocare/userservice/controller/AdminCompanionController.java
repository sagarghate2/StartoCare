package com.startocare.userservice.controller;

import com.startocare.userservice.dto.request.CompanionVerificationRequest;
import com.startocare.userservice.dto.response.CompanionApplicationResponse;
import com.startocare.userservice.enums.CompanionVerificationStatus;
import com.startocare.userservice.enums.Role;
import com.startocare.userservice.security.UserPrincipal;
import com.startocare.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/companions")
public class AdminCompanionController {

    private final UserService userService;

    public AdminCompanionController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/pending")
    public List<CompanionApplicationResponse> getPendingApplications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        requireAdmin(principal);
        return userService.getPendingCompanionApplications().stream()
                .map(CompanionApplicationResponse::from)
                .toList();
    }

    @PatchMapping("/{companionId}/verification")
    public CompanionApplicationResponse updateVerification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companionId,
            @Valid @RequestBody CompanionVerificationRequest request
    ) {
        requireAdmin(principal);
        if (request.status() != CompanionVerificationStatus.VERIFIED
                && request.status() != CompanionVerificationStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification status must be VERIFIED or REJECTED"
            );
        }
        return CompanionApplicationResponse.from(
                userService.updateCompanionVerification(companionId, request.status()));
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.role() != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN users may manage companion verification");
        }
    }
}
