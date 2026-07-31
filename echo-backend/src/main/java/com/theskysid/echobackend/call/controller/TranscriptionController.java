package com.theskysid.echobackend.call.controller;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.call.dto.CallTranscriptDTO;
import com.theskysid.echobackend.call.dto.TranscribeRequestDTO;
import com.theskysid.echobackend.call.entity.CallTranscript;
import com.theskysid.echobackend.call.repository.CallTranscriptRepository;
import com.theskysid.echobackend.call.service.DeepgramService;
import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.repository.ChannelRepository;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/channels")
public class TranscriptionController {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private DeepgramService deepgramService;

    @Autowired
    private CallTranscriptRepository callTranscriptRepository;

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * POST /api/channels/{channelId}/transcribe — transcribe a hosted call
     * recording via Deepgram and persist the result. Members only.
     */
    @PostMapping("/{channelId}/transcribe")
    public ResponseEntity<?> transcribe(@PathVariable Long channelId,
                                        @RequestBody TranscribeRequestDTO request,
                                        Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());

            if (!channelService.isMember(currentUser, channelId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You are not a member of this channel"));
            }

            Channel channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new RuntimeException("Channel not found"));

            String transcript = deepgramService.transcribe(request.getAudioUrl());

            CallTranscript saved = callTranscriptRepository.save(CallTranscript.builder()
                    .channel(channel)
                    .audioUrl(request.getAudioUrl())
                    .fullTranscript(transcript)
                    .build());

            return ResponseEntity.ok(toDTO(saved));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private CallTranscriptDTO toDTO(CallTranscript transcript) {
        return CallTranscriptDTO.builder()
                .id(transcript.getId())
                .channelId(transcript.getChannel().getId())
                .audioUrl(transcript.getAudioUrl())
                .fullTranscript(transcript.getFullTranscript())
                .createdAt(transcript.getCreatedAt())
                .build();
    }
}
