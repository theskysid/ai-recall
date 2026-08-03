package com.theskysid.echobackend.auth.controller;

import com.theskysid.echobackend.auth.dto.GoogleAuthDTO;
import com.theskysid.echobackend.auth.service.GoogleOAuthService;
import com.theskysid.echobackend.auth.service.GoogleOAuthService.GoogleAuthResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/google")
public class GoogleAuthController {

    @Autowired
    private GoogleOAuthService googleOAuthService;

    @Value("${app.secure-cookie:true}")
    private boolean secureCookie;

    /**
     * POST /auth/google/login
     * Accepts a Google ID token from the frontend, verifies it server-side,
     * finds or creates the user, and issues a JWT cookie + UserDTO.
     */
    @PostMapping("/login")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleAuthDTO request) {
        try {
            GoogleAuthResult result = googleOAuthService.authenticateGoogleToken(request.getIdToken());
            ResponseCookie cookie = ResponseCookie.from("JWT", result.token())
                    .httpOnly(true)
                    .secure(secureCookie)
                    .path("/")
                    .maxAge(60 * 60)
                    .sameSite("Strict")
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(result.userDTO());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
