package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.messaging.entity.ChatMessage;
import jakarta.annotation.PreDestroy;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OnlineUserService {

    private static final long DISCONNECT_GRACE_PERIOD_SECONDS = 2;

    private final SimpMessageSendingOperations messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "echo-online-user-presence");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, Set<String>> sessionsByUsername = new HashMap<>();
    private final Map<String, String> usernameBySessionId = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingOfflineTasks = new HashMap<>();

    public OnlineUserService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public synchronized boolean registerSession(String username, String sessionId) {
        if (username == null || username.isBlank() || sessionId == null || sessionId.isBlank()) {
            return false;
        }

        String existingUsername = usernameBySessionId.get(sessionId);
        if (username.equals(existingUsername)) {
            return false;
        }

        boolean reconnectingDuringGrace = cancelPendingOffline(username);
        Set<String> activeSessions = sessionsByUsername.computeIfAbsent(username, ignored -> new HashSet<>());
        boolean shouldBroadcastJoin = activeSessions.isEmpty() && !reconnectingDuringGrace;

        activeSessions.add(sessionId);
        usernameBySessionId.put(sessionId, username);
        return shouldBroadcastJoin;
    }

    public synchronized void unregisterSession(String sessionId) {
        String username = usernameBySessionId.remove(sessionId);
        if (username == null) {
            return;
        }

        Set<String> activeSessions = sessionsByUsername.get(username);
        if (activeSessions == null) {
            return;
        }

        activeSessions.remove(sessionId);
        if (!activeSessions.isEmpty()) {
            return;
        }

        ScheduledFuture<?> offlineTask = scheduler.schedule(
                () -> broadcastLeaveIfStillOffline(username),
                DISCONNECT_GRACE_PERIOD_SECONDS,
                TimeUnit.SECONDS);
        pendingOfflineTasks.put(username, offlineTask);
    }

    public synchronized boolean isOnline(String username) {
        return sessionsByUsername.containsKey(username);
    }

    public synchronized Set<String> getOnlineUsernames() {
        return Collections.unmodifiableSet(new HashSet<>(sessionsByUsername.keySet()));
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void broadcastLeaveIfStillOffline(String username) {
        synchronized (this) {
            ScheduledFuture<?> task = pendingOfflineTasks.get(username);
            Set<String> activeSessions = sessionsByUsername.get(username);
            if (task == null || (activeSessions != null && !activeSessions.isEmpty())) {
                return;
            }

            pendingOfflineTasks.remove(username);
            sessionsByUsername.remove(username);
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setType(ChatMessage.MessageType.LEAVE);
        chatMessage.setSender(username);
        chatMessage.setTimestamp(LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/public", chatMessage);
    }

    private boolean cancelPendingOffline(String username) {
        ScheduledFuture<?> pendingTask = pendingOfflineTasks.remove(username);
        if (pendingTask == null) {
            return false;
        }

        pendingTask.cancel(false);
        return true;
    }
}
