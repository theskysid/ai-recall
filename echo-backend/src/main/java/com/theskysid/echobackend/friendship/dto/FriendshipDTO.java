package com.theskysid.echobackend.friendship.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FriendshipDTO {
    private Long id;
    private String requesterUsername;
    private String addresseeUsername;
    private String status;
    private LocalDateTime createdAt;
}
