package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsOtpService {

    @Autowired
    private OtpService otpService;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    public void sendOtp(String phone) {
        String normalizedPhone = IdentifierNormalizer.normalizePhone(phone);
        String otp = otpService.createOrUpdateForPhone(normalizedPhone);
        Message.creator(
                new PhoneNumber(normalizedPhone),
                new PhoneNumber(twilioPhoneNumber),
                "Your Echo Messaging OTP code is: " + otp + ". Expires in " + otpExpiryMinutes + " minutes.").create();
    }
}
