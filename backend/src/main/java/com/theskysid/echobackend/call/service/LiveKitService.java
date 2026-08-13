package com.theskysid.echobackend.call.service;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import jakarta.annotation.PostConstruct;
import livekit.LivekitModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class LiveKitService {

    private static final Logger logger = LoggerFactory.getLogger(LiveKitService.class);

    @Value("${livekit.url:}")
    private String url;

    @Value("${livekit.api-key:}")
    private String apiKey;

    @Value("${livekit.api-secret:}")
    private String apiSecret;

    @Value("${livekit.token-ttl-minutes:60}")
    private long tokenTtlMinutes;

    /**
     * Mint a LiveKit JWT that grants the participant permission to join and
     * publish/subscribe in the given room.
     *
     * @param roomName        the LiveKit room name (the channel id)
     * @param identity        the participant's unique identity (the user id)
     * @param participantName the display name shown to other participants
     */
    public String createToken(String roomName, String identity, String participantName) {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new RuntimeException("LiveKit is not configured");
        }

        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(identity);
        token.setName(participantName);
        token.setExpiration(new Date(System.currentTimeMillis() + tokenTtlMinutes * 60_000L));
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(true),
                new CanSubscribe(true));

        return token.toJwt();
    }

    /**
     * The LiveKit server URL clients should connect to (may be blank if unset).
     */
    public String getUrl() {
        return url;
    }

    // Server-side API client, used to read who is in a room. Null when LiveKit
    // is unconfigured, which just means "no call is running" to callers.
    private RoomServiceClient roomServiceClient;

    @PostConstruct
    void initRoomServiceClient() {
        if (url == null || url.isBlank() || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            return;
        }
        try {
            // Clients connect over wss://; the server API is the same host over https://.
            roomServiceClient = RoomServiceClient.create(
                    url.replaceFirst("^ws", "http"), apiKey, apiSecret);
        } catch (Exception e) {
            logger.warn("Could not build the LiveKit room service client: {}", e.getMessage());
        }
    }

    /**
     * How many participants are in a channel's call right now. Returns 0 when
     * nobody has joined (LiveKit drops empty rooms), when LiveKit is not
     * configured, or when the API call fails — a chat that can't reach LiveKit
     * should hide the call bar, not break.
     */
    public int getParticipantCount(String roomName) {
        if (roomServiceClient == null) {
            return 0;
        }
        try {
            List<LivekitModels.Room> rooms =
                    roomServiceClient.listRooms(List.of(roomName)).execute().body();
            return (rooms == null || rooms.isEmpty()) ? 0 : rooms.get(0).getNumParticipants();
        } catch (Exception e) {
            logger.warn("Could not read LiveKit room {}: {}", roomName, e.getMessage());
            return 0;
        }
    }
}
