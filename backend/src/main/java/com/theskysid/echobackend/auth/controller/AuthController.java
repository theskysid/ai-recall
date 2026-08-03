package com.theskysid.echobackend.auth.controller;

import com.theskysid.echobackend.auth.dto.request.LoginRequestDTO;
import com.theskysid.echobackend.auth.dto.request.RegisterRequestDTO;
import com.theskysid.echobackend.auth.dto.request.SignupOtpRequestDTO;
import com.theskysid.echobackend.auth.dto.response.LoginResponseDTO;
import com.theskysid.echobackend.user.dto.UserDTO;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import com.theskysid.echobackend.auth.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.secure-cookie:false}")
    private boolean secureCookie;

    @PostMapping("/signup")
    public ResponseEntity<UserDTO> signup(@RequestBody RegisterRequestDTO registerRequestDTO) {
        return ResponseEntity.ok(authenticationService.signup(registerRequestDTO));
    }

    @PostMapping("/login")
    public ResponseEntity<UserDTO> login(@RequestBody LoginRequestDTO loginRequestDTO) {
        LoginResponseDTO loginResponseDTO = authenticationService.login(loginRequestDTO);
        ResponseCookie responseCookie = ResponseCookie.from("JWT", loginResponseDTO.getToken())
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(60 * 60)
                .sameSite("Lax")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(loginResponseDTO.getUserDTO());
    }

    @PostMapping("/signup/verify")
    public ResponseEntity<?> verifySignup(@RequestBody SignupOtpRequestDTO request) {
        try {
            LoginResponseDTO signupResponse = authenticationService.signupWithOtp(request);
            ResponseCookie responseCookie = ResponseCookie.from("JWT", signupResponse.getToken())
                    .httpOnly(true)
                    .secure(secureCookie)
                    .path("/")
                    .maxAge(60 * 60)
                    .sameSite("Lax")
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .body(signupResponse.getUserDTO());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout() {
        return authenticationService.logout();
    }

    @GetMapping("/getonlineusers")
    public ResponseEntity<List<String>> getOnlineUsers() {
        return ResponseEntity.ok(authenticationService.getOnlineUsers());
    }

    @GetMapping("/getcurrentuser")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("USER NOT AUTHORIZED");
        }
        User user = authenticationService.resolveAuthenticatedUser(authentication.getName());
        return ResponseEntity.ok(authenticationService.convertToUserDTO(user));
    }
}