package com.startocare.userservice.controller;

import com.startocare.userservice.dto.request.UpdateProfileRequest;
import com.startocare.userservice.dto.response.UserResponse;
import com.startocare.userservice.security.UserPrincipal;
import com.startocare.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return UserResponse.from(userService.getById(principal.userId()));
    }

    @PatchMapping("/me")
    public UserResponse updateCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return UserResponse.from(
                userService.updateProfile(principal.userId(), request.fullName(), request.phoneNumber()));
    }
}
