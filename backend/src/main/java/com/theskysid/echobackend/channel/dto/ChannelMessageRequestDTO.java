package com.theskysid.echobackend.channel.dto;

import lombok.Data;

@Data
public class ChannelMessageRequestDTO {
    private String sender;
    private String content;
    private String color;
    private String type;
}
