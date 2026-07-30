package com.theskysid.echobackend.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.theskysid.echobackend.auth.jwt.JwtService;
import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import com.theskysid.echobackend.user.dto.UserDTO;
import com.theskysid.echobackend.user.entity.AuthProvider;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleOAuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthenticationService authenticationService;

    @Value("${google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    public GoogleAuthResult authenticateGoogleToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null)
                throw new RuntimeException("Invalid Google ID token");

            GoogleIdToken.Payload payload = idToken.getPayload();
            String googleId = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            User user = userRepository.findByGoogleId(googleId).orElseGet(() -> {
                User existing = userRepository.findByEmailIgnoreCase(IdentifierNormalizer.normalizeEmail(email)).orElse(null);
                if (existing != null) {
                    existing.setGoogleId(googleId);
                    existing.setAuthProvider(AuthProvider.GOOGLE);
                    return userRepository.save(existing);
                }
                User newUser = new User();
                newUser.setGoogleId(googleId);
                newUser.setEmail(IdentifierNormalizer.normalizeEmail(email));
                newUser.setUsername(name != null ? name : email);
                newUser.setAuthProvider(AuthProvider.GOOGLE);
                return userRepository.save(newUser);
            });

            return new GoogleAuthResult(jwtService.generateToken(user), authenticationService.convertToUserDTO(user));

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google authentication failed: " + e.getMessage(), e);
        }
    }

    public record GoogleAuthResult(String token, UserDTO userDTO) {
    }
}
