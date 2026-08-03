package com.theskysid.echobackend.channel.controller;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.channel.dto.ChannelDTO;
import com.theskysid.echobackend.channel.dto.ChannelMessageDTO;
import com.theskysid.echobackend.channel.dto.CreateChannelRequestDTO;
import com.theskysid.echobackend.channel.dto.JoinChannelRequestDTO;
import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMessage;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * GET /api/channels — list the channels the current user belongs to.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> listChannels(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
        List<ChannelDTO> channels = channelService.listMemberships(currentUser).stream()
                .map(membership -> toChannelDTO(membership.getChannel(), currentUser, membership.getJoinedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(channels);
    }

    /**
     * POST /api/channels — create a channel. The creator becomes the owner.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> createChannel(@RequestBody CreateChannelRequestDTO request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            Channel channel = channelService.createChannel(currentUser, request.getName(), request.getDescription());
            return ResponseEntity.ok(toChannelDTO(channel, currentUser, channel.getCreatedAt()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/channels/join — join a channel using an invite code.
     */
    @PostMapping("/join")
    @Transactional
    public ResponseEntity<?> joinChannel(@RequestBody JoinChannelRequestDTO request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            Channel channel = channelService.joinChannel(currentUser, request.getInviteCode());
            return ResponseEntity.ok(toChannelDTO(channel, currentUser, LocalDateTime.now()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/channels/{id}/leave — leave a channel.
     */
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<?> leaveChannel(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            channelService.leaveChannel(currentUser, id);
            return ResponseEntity.ok(Map.of("message", "Left channel"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/channels/{id}/messages — CHAT history for a channel the user belongs to.
     */
    @GetMapping("/{id}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getChannelMessages(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            List<ChannelMessageDTO> messages = channelService.getChannelHistory(currentUser, id).stream()
                    .map(this::toChannelMessageDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(messages);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private ChannelMessageDTO toChannelMessageDTO(ChannelMessage message) {
        return ChannelMessageDTO.builder()
                .id(message.getId())
                .channelId(message.getChannel().getId())
                .sender(message.getSender().getUsername())
                .content(message.getContent())
                .color(message.getColor())
                .type(message.getType().name())
                .timestamp(message.getTimestamp())
                .build();
    }

    private ChannelDTO toChannelDTO(Channel channel, User currentUser, LocalDateTime joinedAt) {
        return ChannelDTO.builder()
                .id(channel.getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .inviteCode(channel.getInviteCode())
                .ownerUsername(channel.getOwner().getUsername())
                .owner(channel.getOwner().getId().equals(currentUser.getId()))
                .memberCount(channelService.getMemberCount(channel))
                .createdAt(channel.getCreatedAt())
                .joinedAt(joinedAt)
                .build();
    }
}
