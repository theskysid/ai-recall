package com.theskysid.echobackend.auth.dto.response;

import com.theskysid.echobackend.user.dto.UserDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDTO {

    private String token;
    private UserDTO userDTO;
}
