package com.theskysid.echobackend.call.service;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class LiveKitService {

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
}
