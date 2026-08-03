package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpService {

    @Autowired
    private OtpService otpService;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    public void sendOtp(String email) {
        String normalizedEmail = IdentifierNormalizer.normalizeEmail(email);
        String otp = otpService.createOrUpdateForEmail(normalizedEmail);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(normalizedEmail);
        message.setSubject("Echo Messaging - Your OTP Code");
        message.setText("Your OTP code is: " + otp + "\n\nThis code expires in " + otpExpiryMinutes
                + " minutes.\nDo not share this code with anyone.");
        mailSender.send(message);
    }
}
