package com.theskysid.echobackend.user.dto;

import lombok.Data;

@Data
public class ProfileUpdateDTO {
    private String displayName;
    private String bio;
    private String username;
}
