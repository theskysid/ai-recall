package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpService {

    @Autowired
    private OtpService otpService;

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtp(String email) {
        String normalizedEmail = IdentifierNormalizer.normalizeEmail(email);
        String otp = otpService.createForEmail(normalizedEmail);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(normalizedEmail);
        message.setSubject("Echo Messaging - Your OTP Code");
        message.setText("Your OTP code is: " + otp + "\n\nThis code expires in " + OtpService.expiryMinutes()
                + " minutes.\nDo not share this code with anyone.");
        mailSender.send(message);
    }
}
