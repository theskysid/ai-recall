package com.theskysid.echobackend.call.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallStatusDTO {

    // True while at least one person is in the channel's call.
    private boolean active;

    private int participants;
}
