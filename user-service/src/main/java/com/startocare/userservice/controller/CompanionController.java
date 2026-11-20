package com.startocare.userservice.controller;

import com.startocare.userservice.dto.response.CompanionSearchResponse;
import com.startocare.userservice.enums.Role;
import com.startocare.userservice.security.UserPrincipal;
import com.startocare.userservice.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companions")
public class CompanionController {

    private final UserService userService;

    public CompanionController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public List<CompanionSearchResponse> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam double latitude,
            @RequestParam double longitude
    ) {
        if (principal.role() != Role.FAMILY) {
            throw new AccessDeniedException("Only FAMILY users may search for companions");
        }
        return userService.searchVerifiedCompanions(latitude, longitude);
    }
}
