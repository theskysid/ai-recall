package com.theskysid.echobackend.channel.service;

import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMembership;
import com.theskysid.echobackend.channel.repository.ChannelMembershipRepository;
import com.theskysid.echobackend.channel.repository.ChannelRepository;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class ChannelService {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMembershipRepository channelMembershipRepository;

    // Unambiguous alphabet (no 0/O/1/I) for readable, shareable invite codes.
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();

    /**
     * Create a channel owned by the given user. The owner is automatically added
     * as the first member, and a unique shareable invite code is generated.
     */
    @Transactional
    public Channel createChannel(User owner, String name, String description) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isBlank()) {
            throw new RuntimeException("Channel name is required");
        }

        Channel channel = Channel.builder()
                .name(trimmedName)
                .description(description == null ? null : description.trim())
                .inviteCode(generateUniqueInviteCode())
                .owner(owner)
                .build();

        Channel saved = channelRepository.save(channel);

        ChannelMembership ownerMembership = ChannelMembership.builder()
                .channel(saved)
                .user(owner)
                .build();
        channelMembershipRepository.save(ownerMembership);

        return saved;
    }

    /**
     * Join a channel using its invite code. Fails if the code is invalid or the
     * user is already a member.
     */
    @Transactional
    public Channel joinChannel(User user, String inviteCode) {
        String normalizedCode = inviteCode == null ? "" : inviteCode.trim().toUpperCase();
        if (normalizedCode.isBlank()) {
            throw new RuntimeException("Invite code is required");
        }

        Channel channel = channelRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new RuntimeException("Invalid invite code"));

        if (channelMembershipRepository.existsByChannelAndUser(channel, user)) {
            throw new RuntimeException("You are already a member of this channel");
        }

        ChannelMembership membership = ChannelMembership.builder()
                .channel(channel)
                .user(user)
                .build();
        channelMembershipRepository.save(membership);

        return channel;
    }

    /**
     * Leave a channel. If the owner leaves, ownership transfers to the
     * longest-standing remaining member. If no members remain, the channel is
     * deleted.
     */
    @Transactional
    public void leaveChannel(User user, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        ChannelMembership membership = channelMembershipRepository.findByChannelAndUser(channel, user)
                .orElseThrow(() -> new RuntimeException("You are not a member of this channel"));

        channelMembershipRepository.delete(membership);
        channelMembershipRepository.flush();

        boolean wasOwner = channel.getOwner().getId().equals(user.getId());
        if (!wasOwner) {
            return;
        }

        // Owner left — find the longest-standing remaining member to inherit the channel.
        List<ChannelMembership> remaining = channelMembershipRepository.findByChannelOrderByJoinedAtAsc(channel);
        if (remaining.isEmpty()) {
            channelRepository.delete(channel);
            return;
        }

        channel.setOwner(remaining.get(0).getUser());
        channelRepository.save(channel);
    }

    /**
     * List the memberships (and therefore channels) the user currently belongs to,
     * most recently joined first.
     */
    @Transactional(readOnly = true)
    public List<ChannelMembership> listMemberships(User user) {
        return channelMembershipRepository.findByUser(user);
    }

    /**
     * Number of members currently in a channel.
     */
    @Transactional(readOnly = true)
    public long getMemberCount(Channel channel) {
        return channelMembershipRepository.countByChannel(channel);
    }

    /**
     * Generate a random invite code that is not already in use.
     */
    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!channelRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Could not generate a unique invite code, please try again");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_ALPHABET.charAt(random.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
