package com.theskysid.echobackend.auth.dto.request;

import lombok.Data;

@Data
public class OtpRequestDTO {
    private String email;
    private String phone;
}
