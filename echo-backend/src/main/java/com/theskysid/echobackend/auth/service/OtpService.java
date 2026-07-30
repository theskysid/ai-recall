package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.auth.otp.entity.OtpVerification;
import com.theskysid.echobackend.auth.otp.entity.OtpVerification.IdentifierType;
import com.theskysid.echobackend.auth.otp.repository.OtpVerificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.rate-limit.max-requests:3}")
    private int maxOtpRequests;

    @Value("${otp.rate-limit.window-minutes:10}")
    private int rateLimitWindowMinutes;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateOtp() {
        return String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
    }

    public String createOrUpdateForEmail(String email) {
        return generateAndSaveOtp(email, IdentifierType.EMAIL);
    }

    public String createOrUpdateForPhone(String phone) {
        return generateAndSaveOtp(phone, IdentifierType.PHONE);
    }

    private String generateAndSaveOtp(String identifier, IdentifierType type) {
        OtpVerification record = otpRepository
                .findByIdentifierAndType(identifier, type)
                .orElseGet(() -> {
                    OtpVerification o = new OtpVerification();
                    o.setIdentifier(identifier);
                    o.setType(type);
                    o.setRequestCount(0);
                    o.setWindowStart(LocalDateTime.now());
                    return o;
                });

        checkRateLimit(record);

        String otp = generateOtp();
        record.setOtpCode(otp);
        record.setExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        record.setRequestCount(record.getRequestCount() + 1);
        otpRepository.save(record);
        return otp;
    }

    public boolean verifyOtp(String identifier, IdentifierType type, String otpCode) {
        OtpVerification record = otpRepository
                .findByIdentifierAndType(identifier, type)
                .orElseThrow(() -> new RuntimeException("No OTP found for " + identifier));

        if (LocalDateTime.now().isAfter(record.getExpiry())) {
            otpRepository.delete(record);
            throw new RuntimeException("OTP expired");
        }

        if (!record.getOtpCode().equals(otpCode)) {
            throw new RuntimeException("Invalid OTP");
        }

        otpRepository.delete(record);
        return true;
    }

    private void checkRateLimit(OtpVerification record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getWindowStart() == null ||
                now.isAfter(record.getWindowStart().plusMinutes(rateLimitWindowMinutes))) {
            record.setRequestCount(0);
            record.setWindowStart(now);
            return;
        }
        if (record.getRequestCount() >= maxOtpRequests) {
            throw new RuntimeException("OTP rate limit exceeded. Try again after "
                    + rateLimitWindowMinutes + " minutes.");
        }

    }
}

