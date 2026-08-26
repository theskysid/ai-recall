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
import com.theskysid.echobackend.memory.service.MemoryIngestionService;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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
    private MemoryIngestionService memoryIngestionService;

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * POST /api/channels/{channelId}/transcribe — transcribe a hosted call
     * recording via Deepgram and persist the result. Members only.
     */
    @PostMapping("/{channelId}/transcribe")
    public CallTranscriptDTO transcribe(@PathVariable Long channelId,
                                        @RequestBody TranscribeRequestDTO request,
                                        Authentication authentication) {
        Channel channel = memberChannel(channelId, authentication);
        String transcript = deepgramService.transcribe(request.getAudioUrl());
        return toDTO(saveAndIngest(channel, request.getAudioUrl(), transcript));
    }

    /**
     * POST /api/channels/{channelId}/recording — accept a call recording captured
     * in the browser, transcribe the bytes directly via Deepgram and persist the
     * result. Members only.
     */
    @PostMapping("/{channelId}/recording")
    public ResponseEntity<?> uploadRecording(@PathVariable Long channelId,
                                             @RequestParam("file") MultipartFile file,
                                             Authentication authentication) {
        Channel channel = memberChannel(channelId, authentication);

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Recording is empty");
        }

        String transcript;
        try {
            transcript = deepgramService.transcribe(file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Could not read the uploaded recording");
        }

        // A silent call transcribes to nothing — don't file an empty record.
        if (transcript.isBlank()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(toDTO(saveAndIngest(channel, "browser-recording", transcript)));
    }

    /**
     * GET /api/channels/{channelId}/transcripts — list saved call transcripts
     * for the channel, most recent first. Members only.
     */
    @GetMapping("/{channelId}/transcripts")
    @Transactional(readOnly = true)
    public List<CallTranscriptDTO> listTranscripts(@PathVariable Long channelId, Authentication authentication) {
        Channel channel = memberChannel(channelId, authentication);
        return callTranscriptRepository.findByChannel(channel).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Resolve the channel, having checked the caller is a member of it — every
     * endpoint here needs both.
     */
    private Channel memberChannel(Long channelId, Authentication authentication) {
        User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
        channelService.requireMember(currentUser, channelId);
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));
    }

    /**
     * Persist a transcript and kick off chunk + embed into vector memory in the
     * background (fire-and-forget).
     */
    private CallTranscript saveAndIngest(Channel channel, String audioUrl, String transcript) {
        CallTranscript saved = callTranscriptRepository.save(CallTranscript.builder()
                .channel(channel)
                .audioUrl(audioUrl)
                .fullTranscript(transcript)
                .build());

        memoryIngestionService.ingestTranscript(saved);
        return saved;
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
