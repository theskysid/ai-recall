package com.theskysid.echobackend.call.controller;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.call.dto.CallStatusDTO;
import com.theskysid.echobackend.call.dto.CallTokenDTO;
import com.theskysid.echobackend.call.service.LiveKitService;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/channels")
public class CallController {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private LiveKitService liveKitService;

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * GET /api/channels/{channelId}/call-token — mint a LiveKit token so the
     * current user can join the channel's WebRTC call. Only members are allowed.
     */
    @GetMapping("/{channelId}/call-token")
    public ResponseEntity<?> getCallToken(@PathVariable Long channelId, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());

            if (!channelService.isMember(currentUser, channelId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You are not a member of this channel"));
            }

            String identity = String.valueOf(currentUser.getId());
            String token = liveKitService.createToken(
                    String.valueOf(channelId), identity, currentUser.getUsername());

            CallTokenDTO response = CallTokenDTO.builder()
                    .token(token)
                    .url(liveKitService.getUrl())
                    .room(String.valueOf(channelId))
                    .identity(identity)
                    .build();
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * GET /api/channels/{channelId}/call-status — how many people are in the
     * channel's call right now, so the chat can offer to join one already in
     * progress. Members only.
     */
    @GetMapping("/{channelId}/call-status")
    public ResponseEntity<?> getCallStatus(@PathVariable Long channelId, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());

            if (!channelService.isMember(currentUser, channelId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You are not a member of this channel"));
            }

            int participants = liveKitService.getParticipantCount(String.valueOf(channelId));
            return ResponseEntity.ok(CallStatusDTO.builder()
                    .active(participants > 0)
                    .participants(participants)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
