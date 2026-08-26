package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.auth.otp.entity.OtpVerification;
import com.theskysid.echobackend.auth.otp.entity.OtpVerification.IdentifierType;
import com.theskysid.echobackend.auth.otp.repository.OtpVerificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {

    /**
     * Email is the only identifier that can hold an OTP — phone/SMS was removed.
     * The column still exists so legacy PHONE rows keep their meaning, so this is
     * pinned here rather than threaded through every caller's signature.
     */
    private static final IdentifierType TYPE = IdentifierType.EMAIL;

    private static final int EXPIRY_MINUTES = 5;
    private static final int MAX_REQUESTS = 3;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 10;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private OtpVerificationRepository otpRepository;

    public String createForEmail(String email) {
        OtpVerification record = otpRepository
                .findByIdentifierAndType(email, TYPE)
                .orElseGet(() -> {
                    OtpVerification o = new OtpVerification();
                    o.setIdentifier(email);
                    o.setType(TYPE);
                    o.setRequestCount(0);
                    o.setWindowStart(LocalDateTime.now());
                    return o;
                });

        checkRateLimit(record);

        String otp = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        record.setOtpCode(otp);
        record.setExpiry(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        record.setRequestCount(record.getRequestCount() + 1);
        otpRepository.save(record);
        return otp;
    }

    public void verifyOtp(String email, String otpCode) {
        OtpVerification record = otpRepository
                .findByIdentifierAndType(email, TYPE)
                .orElseThrow(() -> new RuntimeException("No OTP found for " + email));

        if (LocalDateTime.now().isAfter(record.getExpiry())) {
            otpRepository.delete(record);
            throw new RuntimeException("OTP expired");
        }

        if (!record.getOtpCode().equals(otpCode)) {
            throw new RuntimeException("Invalid OTP");
        }

        otpRepository.delete(record);
    }

    private void checkRateLimit(OtpVerification record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getWindowStart() == null ||
                now.isAfter(record.getWindowStart().plusMinutes(RATE_LIMIT_WINDOW_MINUTES))) {
            record.setRequestCount(0);
            record.setWindowStart(now);
            return;
        }
        if (record.getRequestCount() >= MAX_REQUESTS) {
            throw new RuntimeException("OTP rate limit exceeded. Try again after "
                    + RATE_LIMIT_WINDOW_MINUTES + " minutes.");
        }
    }

    static int expiryMinutes() {
        return EXPIRY_MINUTES;
    }
}
