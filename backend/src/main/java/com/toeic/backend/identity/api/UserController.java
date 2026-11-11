package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.identity.application.UserProfile;
import com.toeic.backend.identity.application.UserProfileService;
import com.toeic.backend.identity.application.PasswordChangeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserProfileService profiles;
    private final PasswordChangeService passwords;

    public UserController(UserProfileService profiles, PasswordChangeService passwords) {
        this.profiles = profiles;
        this.passwords = passwords;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal SessionPrincipal principal) {
        return profiles.me(principal.userId());
    }

    @PatchMapping("/me")
    public UserProfile update(@AuthenticationPrincipal SessionPrincipal principal,
                              @RequestBody UpdateProfileRequest request) {
        return profiles.update(principal.userId(), request.toCommand());
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal SessionPrincipal principal,
                               @RequestBody ChangePasswordRequest request) {
        passwords.change(principal.userId(), request.toCommand());
    }
}
