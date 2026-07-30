package com.theskysid.echobackend.user.dto;

import lombok.Data;

@Data
public class UserDTO {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String displayName;
    private String bio;
    private String googleId;
    private String authProvider;
    private boolean isOnline;
    private String token;
}
