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
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    public List<ChannelDTO> listChannels(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return channelService.listMemberships(currentUser).stream()
                .map(membership -> toChannelDTO(membership.getChannel(), currentUser, membership.getJoinedAt()))
                .toList();
    }

    /**
     * POST /api/channels — create a channel. The creator becomes the owner.
     */
    @PostMapping
    @Transactional
    public ChannelDTO createChannel(@RequestBody CreateChannelRequestDTO request, Authentication authentication) {
        User currentUser = currentUser(authentication);
        Channel channel = channelService.createChannel(currentUser, request.getName(), request.getDescription());
        return toChannelDTO(channel, currentUser, channel.getCreatedAt());
    }

    /**
     * POST /api/channels/join — join a channel using an invite code.
     */
    @PostMapping("/join")
    @Transactional
    public ChannelDTO joinChannel(@RequestBody JoinChannelRequestDTO request, Authentication authentication) {
        User currentUser = currentUser(authentication);
        Channel channel = channelService.joinChannel(currentUser, request.getInviteCode());
        return toChannelDTO(channel, currentUser, LocalDateTime.now());
    }

    /**
     * DELETE /api/channels/{id}/leave — leave a channel.
     */
    @DeleteMapping("/{id}/leave")
    public Map<String, String> leaveChannel(@PathVariable Long id, Authentication authentication) {
        channelService.leaveChannel(currentUser(authentication), id);
        return Map.of("message", "Left channel");
    }

    /**
     * GET /api/channels/{id}/messages — CHAT history for a channel the user belongs to.
     */
    @GetMapping("/{id}/messages")
    @Transactional(readOnly = true)
    public List<ChannelMessageDTO> getChannelMessages(@PathVariable Long id, Authentication authentication) {
        return channelService.getChannelHistory(currentUser(authentication), id).stream()
                .map(this::toChannelMessageDTO)
                .toList();
    }

    // ── Helpers ─────────────────────────────────────────────────

    private User currentUser(Authentication authentication) {
        return authenticationService.resolveAuthenticatedUser(authentication.getName());
    }

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
