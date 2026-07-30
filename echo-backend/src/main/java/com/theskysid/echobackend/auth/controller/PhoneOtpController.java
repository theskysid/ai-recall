package com.theskysid.echobackend.auth.controller;

import com.theskysid.echobackend.auth.dto.OtpVerifyDTO;
import com.theskysid.echobackend.auth.dto.request.OtpRequestDTO;
import com.theskysid.echobackend.auth.dto.response.LoginResponseDTO;
import com.theskysid.echobackend.auth.otp.entity.OtpVerification.IdentifierType;
import com.theskysid.echobackend.auth.service.OtpService;
import com.theskysid.echobackend.auth.service.SmsOtpService;
import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/phone-otp")
public class PhoneOtpController {

    @Autowired
    private SmsOtpService smsOtpService;
    @Autowired
    private OtpService otpService;
    @Autowired
    private com.theskysid.echobackend.auth.service.AuthenticationService authenticationService;

    @Value("${app.secure-cookie:true}")
    private boolean secureCookie;

    /**
     * POST /auth/phone-otp/send
     * Send a 6-digit OTP via SMS to the given phone number.
     * Creates a new user if one doesn't exist for this phone.
     * Rate limited: max 3 requests per 10 minutes per phone.
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequestDTO request) {
        try {
            String normalizedPhone = IdentifierNormalizer.normalizePhone(request.getPhone());
            smsOtpService.sendOtp(normalizedPhone);
            return ResponseEntity.ok(Map.of("message", "OTP sent to " + normalizedPhone));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /auth/phone-otp/verify
     * Verify the OTP for the given phone. If valid, issue a JWT cookie and return
     * UserDTO.
     * OTP is invalidated after successful verification.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyDTO request) {
        try {
            String normalizedPhone = IdentifierNormalizer.normalizePhone(request.getPhone());
            otpService.verifyOtp(normalizedPhone, IdentifierType.PHONE, request.getOtp());

            LoginResponseDTO loginResponse = authenticationService.loginWithOtp(normalizedPhone, IdentifierType.PHONE);
            ResponseCookie cookie = ResponseCookie.from("JWT", loginResponse.getToken())
                    .httpOnly(true)
                    .secure(secureCookie)
                    .path("/")
                    .maxAge(60 * 60)
                    .sameSite("Strict")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(loginResponse.getUserDTO());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
