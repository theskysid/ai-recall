package com.theskysid.echobackend.auth.dto;

import lombok.Data;

@Data
public class OtpVerifyDTO {
    private String email;
    private String phone;
    private String otp;
}