package com.theskysid.echobackend.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Timings contain only a fixed event name and duration, never tokens or message content. */
@Component
public class ChatLatencyInterceptor implements ExecutorChannelInterceptor {
    private static final String RECEIVED = "chatReceivedNanos";
    private static final String HANDLING = "chatHandlingNanos";
    private static final String EVENT = "chatEvent";
    private final MeterRegistry registry;

    public ChatLatencyInterceptor(MeterRegistry registry) { this.registry = registry; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers != null && headers.getCommand() == StompCommand.SEND) {
            String event = event(headers.getDestination());
            if (event != null) {
                headers.setHeader(RECEIVED, System.nanoTime());
                headers.setHeader(EVENT, event);
            }
        }
        return message;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (!(handler instanceof SimpAnnotationMethodMessageHandler) || !message.getHeaders().containsKey(RECEIVED)) return message;
        long now = System.nanoTime();
        record("chat.server.wait", message, now - (Long) message.getHeaders().get(RECEIVED));
        return MessageBuilder.fromMessage(message).setHeader(HANDLING, now).build();
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        if (!(handler instanceof SimpAnnotationMethodMessageHandler) || !message.getHeaders().containsKey(HANDLING)) return;
        long now = System.nanoTime();
        record("chat.server.handle", message, now - (Long) message.getHeaders().get(HANDLING));
        record("chat.server.total", message, now - (Long) message.getHeaders().get(RECEIVED));
    }

    /** Preserve internal timing through application broadcasts and user destination resolution. */
    public static Map<String, Object> deliveryHeaders(SimpMessageHeaderAccessor incoming) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setHeader(RECEIVED, incoming.getHeader(RECEIVED));
        headers.setHeader(EVENT, incoming.getHeader(EVENT));
        headers.setLeaveMutable(true);
        return headers.getMessageHeaders();
    }

    public Message<?> timeDelivery(Message<?> message) {
        if (message == null || !message.getHeaders().containsKey(RECEIVED)
                || SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        long duration = System.nanoTime() - (Long) message.getHeaders().get(RECEIVED);
        record("chat.server.delivery", message, duration);
        var headers = SimpMessageHeaderAccessor.wrap(message);
        headers.setNativeHeader("x-chat-server-ms", Double.toString(duration / 1_000_000.0));
        return MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders());
    }

    private void record(String name, Message<?> message, long nanos) {
        registry.timer(name, "event", (String) message.getHeaders().get(EVENT)).record(Math.max(0, nanos), TimeUnit.NANOSECONDS);
    }

    private String event(String destination) {
        if ("/app/dm.sendMessage".equals(destination)) return "dm.send";
        if ("/app/dm.typing".equals(destination)) return "dm.typing";
        if (destination != null && destination.matches("/app/channel/[1-9][0-9]*/send")) return "channel.send";
        return null;
    }
}
