package com.theskysid.echobackend.memory.controller;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.call.entity.CallTranscript;
import com.theskysid.echobackend.call.repository.CallTranscriptRepository;
import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMessage;
import com.theskysid.echobackend.channel.repository.ChannelRepository;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.memory.dto.RagContextDTO;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import com.theskysid.echobackend.memory.service.MemoryIngestionService;
import com.theskysid.echobackend.memory.service.RagService;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Seeding and inspection endpoints for the staleness evaluation. Disabled unless
 * recall.eval.enabled=true, so it is absent from a normal deployment.
 *
 * Chat normally arrives over STOMP and transcripts only ever come back from
 * Deepgram, so neither can be seeded over HTTP without this. Everything here
 * still routes through the production ingestion path — the same embedder, the
 * same decision extractor, the same supersession check — because seeding
 * memory_vectors directly would assume away the behaviour under test.
 *
 * Bodies are plain maps rather than DTOs: these endpoints ship with the eval
 * harness, not the product.
 */
@RestController
@RequestMapping("/api/eval")
@ConditionalOnProperty(name = "recall.eval.enabled", havingValue = "true")
public class EvalController {

    @Autowired
    private ChannelService channelService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private CallTranscriptRepository callTranscriptRepository;

    @Autowired
    private MemoryIngestionService memoryIngestionService;

    @Autowired
    private MemoryVectorRepository memoryVectorRepository;

    @Autowired
    private RagService ragService;

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * POST /api/eval/channels/{channelId}/message — post a chat message as the
     * caller, exactly as ChannelChatController would, minus the WebSocket broadcast.
     */
    @PostMapping("/channels/{channelId}/message")
    public ResponseEntity<?> seedMessage(@PathVariable Long channelId,
                                         @RequestBody Map<String, String> body,
                                         Authentication authentication) {
        return withMember(channelId, authentication, user -> {
            String content = body.get("content");
            if (content == null || content.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
            }
            ChannelMessage saved = channelService.postMessage(channelId, user, content, null);
            return ResponseEntity.ok(Map.of("id", saved.getId()));
        });
    }

    /**
     * POST /api/eval/channels/{channelId}/transcript — file a call transcript from
     * supplied text instead of Deepgram output, then chunk and embed it.
     */
    @PostMapping("/channels/{channelId}/transcript")
    public ResponseEntity<?> seedTranscript(@PathVariable Long channelId,
                                            @RequestBody Map<String, String> body,
                                            Authentication authentication) {
        return withMember(channelId, authentication, user -> {
            String content = body.get("content");
            if (content == null || content.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "content is required"));
            }
            Channel channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new RuntimeException("Channel not found"));

            CallTranscript saved = callTranscriptRepository.save(CallTranscript.builder()
                    .channel(channel)
                    .audioUrl("eval-seed")
                    .fullTranscript(content)
                    .build());

            memoryIngestionService.ingestTranscript(saved);
            return ResponseEntity.ok(Map.of("id", saved.getId()));
        });
    }

    /**
     * GET /api/eval/channels/{channelId}/memory-count — vectors stored so far.
     * Ingestion is @Async, so the harness polls this until it settles rather than
     * sleeping a guessed interval and querying a half-built index.
     */
    @GetMapping("/channels/{channelId}/memory-count")
    public ResponseEntity<?> memoryCount(@PathVariable Long channelId, Authentication authentication) {
        return withMember(channelId, authentication, user ->
                ResponseEntity.ok(Map.of("count", memoryVectorRepository.countByChannelId(channelId))));
    }

    /**
     * GET /api/eval/channels/{channelId}/ask?q= — same RagService call the product
     * endpoint makes, but returning the retrieved chunks and the active retrieval
     * mode as well as the answer, so retrieval and synthesis can be scored apart.
     */
    @GetMapping("/channels/{channelId}/ask")
    public ResponseEntity<?> ask(@PathVariable Long channelId,
                                 @RequestParam("q") String query,
                                 Authentication authentication) {
        return withMember(channelId, authentication, user -> {
            RagContextDTO result = ragService.retrieveContext(String.valueOf(channelId), query);
            return ResponseEntity.ok(result);
        });
    }

    /**
     * Shared auth + membership guard, matching AiController's behaviour.
     */
    private ResponseEntity<?> withMember(Long channelId, Authentication authentication, MemberAction action) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());
            if (!channelService.isMember(currentUser, channelId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You are not a member of this channel"));
            }
            return action.run(currentUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @FunctionalInterface
    private interface MemberAction {
        ResponseEntity<?> run(User currentUser);
    }
}
