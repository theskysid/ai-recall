package com.theskysid.echobackend.auth.controller;

import com.theskysid.echobackend.auth.dto.request.LoginRequestDTO;
import com.theskysid.echobackend.auth.dto.request.RegisterRequestDTO;
import com.theskysid.echobackend.auth.dto.request.SignupOtpRequestDTO;
import com.theskysid.echobackend.auth.dto.response.LoginResponseDTO;
import com.theskysid.echobackend.user.dto.UserDTO;
import com.theskysid.echobackend.auth.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationService authenticationService;

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
    public ResponseEntity<UserDTO> verifySignup(@RequestBody SignupOtpRequestDTO request) {
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
    public UserDTO getCurrentUser(Authentication authentication) {
        return authenticationService.convertToUserDTO(
                authenticationService.resolveAuthenticatedUser(authentication.getName()));
    }
}