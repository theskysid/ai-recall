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
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
    public Map<String, Object> ask(@PathVariable Long channelId,
                                   @RequestParam("q") String query,
                                   Authentication authentication) {
        channelService.requireMember(currentUser(authentication), channelId);

        RagContextDTO result = ragService.retrieveContext(String.valueOf(channelId), query);
        return Map.of(
                "answer", result.getAnswer() == null ? "" : result.getAnswer(),
                "sourceIds", result.getSourceIds());
    }

    /**
     * GET /api/channels/{channelId}/decisions — list extracted decisions for the
     * channel (active + superseded), newest first, for the timeline UI. Members only.
     */
    @GetMapping("/{channelId}/decisions")
    @Transactional(readOnly = true)
    public List<DecisionDTO> decisions(@PathVariable Long channelId, Authentication authentication) {
        channelService.requireMember(currentUser(authentication), channelId);

        return memoryVectorRepository.findDecisionsByChannel(channelId).stream()
                .map(this::toDecisionDTO)
                .toList();
    }

    private User currentUser(Authentication authentication) {
        return authenticationService.resolveAuthenticatedUser(authentication.getName());
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
