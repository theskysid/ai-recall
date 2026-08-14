package com.theskysid.echobackend.memory.controller;

import com.theskysid.echobackend.auth.service.AuthenticationService;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.memory.dto.DecisionDTO;
import com.theskysid.echobackend.memory.dto.RagContextDTO;
import com.theskysid.echobackend.memory.entity.MemoryStatus;
import com.theskysid.echobackend.memory.entity.MemoryVector;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import com.theskysid.echobackend.memory.service.RagService;
import com.theskysid.echobackend.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/channels")
public class AiController {

    @Autowired
    private RagService ragService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private MemoryVectorRepository memoryVectorRepository;

    @Autowired
    private AuthenticationService authenticationService;

    /**
     * GET /api/channels/{channelId}/ask?q={query} — retrieve the most relevant
     * stored memories for the query (channel-scoped), synthesize an answer with
     * the LLM, and return it with the source ids. Members only.
     */
    @GetMapping("/{channelId}/ask")
    public ResponseEntity<?> ask(@PathVariable Long channelId,
                                 @RequestParam("q") String query,
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

            RagContextDTO result = ragService.retrieveContext(String.valueOf(channelId), query);
            return ResponseEntity.ok(Map.of(
                    "answer", result.getAnswer() == null ? "" : result.getAnswer(),
                    "sourceIds", result.getSourceIds()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * GET /api/channels/{channelId}/decisions — list extracted decisions for the
     * channel (active + superseded), newest first, for the timeline UI. Members only.
     */
    @GetMapping("/{channelId}/decisions")
    @Transactional(readOnly = true)
    public ResponseEntity<?> decisions(@PathVariable Long channelId, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            User currentUser = authenticationService.resolveAuthenticatedUser(authentication.getName());

            if (!channelService.isMember(currentUser, channelId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You are not a member of this channel"));
            }

            List<DecisionDTO> decisions = memoryVectorRepository.findDecisionsByChannel(channelId).stream()
                    .map(this::toDecisionDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(decisions);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private DecisionDTO toDecisionDTO(MemoryVector v) {
        MemoryStatus status = v.getStatus() == null ? MemoryStatus.CURRENT : v.getStatus();
        return DecisionDTO.builder()
                .id(v.getId())
                .channelId(v.getChannelId())
                .title(v.getTitle())
                .content(v.getContent())
                .sourceType(v.getSourceType() == null ? null : v.getSourceType().name())
                .sourceId(v.getSourceId())
                .status(status.name())
                .superseded(status == MemoryStatus.SUPERSEDED)
                .conflictsWithId(v.getConflictsWithId())
                .createdAt(v.getCreatedAt())
                .build();
    }
}
