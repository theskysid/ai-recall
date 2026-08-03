package com.theskysid.echobackend.friendship.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendUserDTO {
    private Long id;
    private Long friendshipId;
    private String username;
    private String displayName;
    private boolean online;
    private String friendshipStatus;
}
