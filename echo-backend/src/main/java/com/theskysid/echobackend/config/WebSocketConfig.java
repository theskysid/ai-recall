package com.theskysid.echobackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

   @Value("${allowed-origins:https://echomessaging.duckdns.org,http://localhost:5173}")
   private String allowedOrigins;

   // changes the http to websocket protocol

   @Override
   public void configureMessageBroker(MessageBrokerRegistry config) {

      // The server will SEND messages to clients using these paths.... inside the
      // tunnel we need some highways to provide paths to each endpoints

      // If a message is sent to /topic or /queue or /user,
      // the broker will deliver it to whoever is subscribed.
      // here for the broadcast it to everyone
      config.enableSimpleBroker("/topic", "/queue", "/user");
      // Broker, deliver messages sent to those /user paths.

      // The client will SEND messages to the server using paths that start with /app.
      // Those messages go to your @MessageMapping methods.
      config.setApplicationDestinationPrefixes("/app");

      // When the server sends a private message to a specific user,
      // Spring will use destinations that start with /user.
      // Example: /user/sid/queue/private
      config.setUserDestinationPrefix("/user");
      // Build private message paths using /user.
   }

   @Override
   public void registerStompEndpoints(StompEndpointRegistry registry) {

      // The WebSocket endpoint. The frontend connects to this URL to open the
      // WebSocket connection.
      registry.addEndpoint("/ws")

            // Only these frontend URLs are allowed to open the connection.
            // (localhost:5173 = Vite, localhost:3000 = CRA)
            .setAllowedOriginPatterns(allowedOrigins)

            // Enables SockJS fallback if WebSocket cannot connect.
            .withSockJS();
   }
}
