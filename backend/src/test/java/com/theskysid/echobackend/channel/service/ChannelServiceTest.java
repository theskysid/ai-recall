package com.theskysid.echobackend.channel.service;

import com.theskysid.echobackend.call.repository.CallTranscriptRepository;
import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMembership;
import com.theskysid.echobackend.channel.repository.*;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import com.theskysid.echobackend.memory.service.MemoryIngestionService;
import com.theskysid.echobackend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {
    @Mock ChannelRepository channels;
    @Mock ChannelMembershipRepository memberships;
    @Mock ChannelMessageRepository messages;
    @Mock CallTranscriptRepository transcripts;
    @Mock MemoryVectorRepository memories;
    @Mock MemoryIngestionService ingestion;
    @InjectMocks ChannelService service;
    User owner;
    Channel channel;
    ChannelMembership membership;

    @BeforeEach
    void setup() {
        owner = User.builder().id(1L).username("owner").build();
        channel = Channel.builder().id(42L).owner(owner).build();
        membership = ChannelMembership.builder().channel(channel).user(owner).build();
        when(channels.findByIdForUpdate(42L)).thenReturn(Optional.of(channel));
        when(memberships.findByChannelAndUser(channel, owner)).thenReturn(Optional.of(membership));
    }

    @Test
    void lastMemberLeaveDeletesHistoryBeforeChannel() {
        when(memberships.findByChannelOrderByJoinedAtAsc(channel)).thenReturn(List.of());
        service.leaveChannel(owner, 42L);
        var order = inOrder(memberships, memories, transcripts, messages, channels);
        order.verify(memberships).delete(membership);
        order.verify(memberships).flush();
        order.verify(memories).deleteAllByChannelId(42L);
        order.verify(transcripts).deleteAllByChannel(channel);
        order.verify(messages).deleteAllByChannel(channel);
        order.verify(channels).delete(channel);
    }

    @Test
    void ownerLeaveTransfersOwnershipAndPreservesHistory() {
        var successor = User.builder().id(2L).username("member").build();
        when(memberships.findByChannelOrderByJoinedAtAsc(channel)).thenReturn(List.of(
                ChannelMembership.builder().channel(channel).user(successor).build()));
        service.leaveChannel(owner, 42L);
        assertSame(successor, channel.getOwner());
        verify(channels).save(channel);
        verify(channels, never()).delete(any());
        verifyNoInteractions(memories, transcripts, messages);
    }
}
