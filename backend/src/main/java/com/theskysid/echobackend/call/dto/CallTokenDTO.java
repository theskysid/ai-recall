package com.theskysid.echobackend.call.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallTokenDTO {
    private String token;
    private String url;
    private String room;
    private String identity;
}
