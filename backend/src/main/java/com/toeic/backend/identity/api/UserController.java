package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.identity.application.UserProfile;
import com.toeic.backend.identity.application.UserProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserProfileService profiles;

    public UserController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal SessionPrincipal principal) {
        return profiles.me(principal.userId());
    }
}
