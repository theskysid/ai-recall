package com.theskysid.echobackend.user.controller;

import com.theskysid.echobackend.auth.dto.OtpVerifyDTO;
import com.theskysid.echobackend.auth.dto.GoogleAuthDTO;
import com.theskysid.echobackend.auth.dto.request.OtpRequestDTO;
import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.auth.service.EmailOtpService;
import com.theskysid.echobackend.auth.service.OtpService;
import com.theskysid.echobackend.user.dto.ProfileUpdateDTO;
import com.theskysid.echobackend.user.dto.UserDTO;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private EmailOtpService emailOtpService;

    @Autowired
    private OtpService otpService;

    @Value("${google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    /**
     * GET /api/profile — get current user's profile
     */
    @GetMapping
    public UserDTO getProfile(Authentication authentication) {
        return authenticationService.convertToUserDTO(currentUser(authentication));
    }

    /**
     * PUT /api/profile — update displayName, bio, username
     */
    @PutMapping
    public UserDTO updateProfile(@RequestBody ProfileUpdateDTO request, Authentication authentication) {
        User user = currentUser(authentication);

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getBio() != null) {
            String bio = request.getBio().trim();
            if (bio.length() > 200) bio = bio.substring(0, 200);
            user.setBio(bio);
        }
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            String newUsername = request.getUsername().trim();
            if (IdentifierNormalizer.hasWhitespace(newUsername)) {
                throw new RuntimeException("Username cannot contain spaces");
            }
            if (!newUsername.equalsIgnoreCase(user.getUsername())) {
                if (userRepository.findByUsernameIgnoreCase(newUsername).isPresent()) {
                    throw new RuntimeException("Username already taken");
                }
                user.setUsername(newUsername);
            }
        }

        return authenticationService.convertToUserDTO(userRepository.save(user));
    }

    /**
     * POST /api/profile/link-email/send — send OTP to email for linking
     */
    @PostMapping("/link-email/send")
    public Map<String, String> sendLinkEmailOtp(@RequestBody OtpRequestDTO request, Authentication authentication) {
        String normalizedEmail = IdentifierNormalizer.normalizeEmail(request.getEmail());
        User currentUser = currentUser(authentication);
        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(existing -> {
            if (!existing.getId().equals(currentUser.getId())) {
                throw new RuntimeException("This email is already linked to another account");
            }
        });
        emailOtpService.sendOtp(normalizedEmail);
        return Map.of("message", "OTP sent to " + normalizedEmail);
    }

    /**
     * POST /api/profile/link-email/verify — verify OTP and link email
     */
    @PostMapping("/link-email/verify")
    public UserDTO verifyLinkEmail(@RequestBody OtpVerifyDTO request, Authentication authentication) {
        String normalizedEmail = IdentifierNormalizer.normalizeEmail(request.getEmail());
        otpService.verifyOtp(normalizedEmail, request.getOtp());
        User user = currentUser(authentication);

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                existing.setEmail(null);
                userRepository.save(existing);
            }
        });

        user.setEmail(normalizedEmail);
        return authenticationService.convertToUserDTO(userRepository.save(user));
    }

    /**
     * POST /api/profile/link-google — link Google account by verifying token
     */
    @PostMapping("/link-google")
    public UserDTO linkGoogle(@RequestBody GoogleAuthDTO request, Authentication authentication) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.getIdToken());
        } catch (Exception e) {
            throw new RuntimeException("Google verification failed");
        }
        if (idToken == null) {
            throw new RuntimeException("Invalid Google token");
        }

        String googleId = idToken.getPayload().getSubject();
        User user = currentUser(authentication);

        // Check if this Google ID is already used by another user
        userRepository.findByGoogleId(googleId).ifPresent(existing -> {
            if (!existing.getId().equals(user.getId())) {
                throw new RuntimeException("This Google account is already linked to another user");
            }
        });

        user.setGoogleId(googleId);
        return authenticationService.convertToUserDTO(userRepository.save(user));
    }

    /**
     * POST /api/profile/unlink-email — remove email from account
     */
    @PostMapping("/unlink-email")
    public UserDTO unlinkEmail(Authentication authentication) {
        User user = currentUser(authentication);
        requireAlternativeAuth(user, "email");
        user.setEmail(null);
        return authenticationService.convertToUserDTO(userRepository.save(user));
    }

    /**
     * POST /api/profile/unlink-google — remove Google from account
     */
    @PostMapping("/unlink-google")
    public UserDTO unlinkGoogle(Authentication authentication) {
        User user = currentUser(authentication);
        requireAlternativeAuth(user, "google");
        user.setGoogleId(null);
        return authenticationService.convertToUserDTO(userRepository.save(user));
    }

    // ── Helpers ─────────────────────────────────────────────────

    private User currentUser(Authentication authentication) {
        return authenticationService.resolveAuthenticatedUser(authentication.getName());
    }

    /**
     * Refuse to remove the last way the user can get back in.
     */
    private void requireAlternativeAuth(User user, String methodToRemove) {
        int remaining = 0;
        if (user.getPassword() != null && !user.getPassword().isEmpty()) remaining++;
        if (user.getEmail() != null && !"email".equals(methodToRemove)) remaining++;
        if (user.getGoogleId() != null && !"google".equals(methodToRemove)) remaining++;
        if (remaining == 0) {
            throw new RuntimeException("Cannot unlink your only login method");
        }
    }
}
