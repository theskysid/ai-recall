package com.theskysid.echobackend.channel.dto;

import lombok.Data;

@Data
public class CreateChannelRequestDTO {
    private String name;
    private String description;
}
