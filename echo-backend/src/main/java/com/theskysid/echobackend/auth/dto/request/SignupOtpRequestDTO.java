package com.theskysid.echobackend.auth.dto.request;

import lombok.Data;

@Data
public class SignupOtpRequestDTO {
    private String username;
    private String identifier;
    private String password;
    private String otp;
}
