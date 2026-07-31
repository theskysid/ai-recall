package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.auth.dto.request.LoginRequestDTO;
import com.theskysid.echobackend.auth.dto.request.RegisterRequestDTO;
import com.theskysid.echobackend.auth.dto.request.SignupOtpRequestDTO;
import com.theskysid.echobackend.auth.dto.response.LoginResponseDTO;
import com.theskysid.echobackend.auth.otp.entity.OtpVerification.IdentifierType;
import com.theskysid.echobackend.auth.util.IdentifierNormalizer;
import com.theskysid.echobackend.user.dto.UserDTO;
import com.theskysid.echobackend.auth.jwt.JwtService;
import com.theskysid.echobackend.user.entity.AuthProvider;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private OtpService otpService;

    @Value("${app.secure-cookie:true}")
    private boolean secureCookie;

    public UserDTO signup(RegisterRequestDTO registerRequestDTO) {
        String username = IdentifierNormalizer.normalizeUsername(registerRequestDTO.getUsername());
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username is required");
        }
        if (registerRequestDTO.getPassword() == null || registerRequestDTO.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }
        if (registerRequestDTO.getEmail() == null || registerRequestDTO.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }

        String email = IdentifierNormalizer.normalizeEmail(registerRequestDTO.getEmail());
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new RuntimeException("Username is already in use");
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new RuntimeException("Email is already in use");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(registerRequestDTO.getPassword()));
        user.setEmail(email);
        user.setAuthProvider(AuthProvider.EMAIL);
        return convertToUserDTO(userRepository.save(user));
    }

    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        String username = IdentifierNormalizer.normalizeUsername(loginRequestDTO.getUsername());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        username, loginRequestDTO.getPassword()));

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return issueLoginResponse(user);
    }

    public LoginResponseDTO loginWithOtp(String identifier, IdentifierType type) {
        User user = resolveUserForOtpLogin(identifier, type)
                .orElseThrow(() -> new RuntimeException("No account linked to this email"));
        return issueLoginResponse(user);
    }

    public User resolveAuthenticatedUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public LoginResponseDTO signupWithOtp(SignupOtpRequestDTO request) {
        String username = IdentifierNormalizer.normalizeUsername(request.getUsername());
        String identifier = IdentifierNormalizer.normalizeIdentifier(request.getIdentifier());

        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username is required");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }
        if (!IdentifierNormalizer.isEmail(identifier)) {
            throw new RuntimeException("A valid email is required");
        }

        String normalizedIdentifier = IdentifierNormalizer.normalizeEmail(identifier);
        otpService.verifyOtp(normalizedIdentifier, IdentifierType.EMAIL, request.getOtp());

        Optional<User> existingUser = findByIdentifier(normalizedIdentifier, IdentifierType.EMAIL);
        Optional<User> usernameOwner = userRepository.findByUsernameIgnoreCase(username);

        if (usernameOwner.isPresent() && existingUser.map(user -> !usernameOwner.get().getId().equals(user.getId())).orElse(true)) {
            throw new RuntimeException("Username is already in use");
        }

        if (existingUser.isPresent() && hasPassword(existingUser.get())) {
            throw new RuntimeException("An account already exists for this identifier. Please log in instead.");
        }

        User user = existingUser.orElseGet(User::new);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(normalizedIdentifier);
        if (user.getAuthProvider() == null) {
            user.setAuthProvider(AuthProvider.EMAIL);
        }

        User saved = userRepository.save(user);
        return issueLoginResponse(saved);
    }

    public ResponseEntity<String> logout() {
        ResponseCookie cookie = ResponseCookie.from("JWT", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("Logged out successfully");
    }

    public List<String> getOnlineUsers() {
        return new java.util.ArrayList<>(onlineUserService.getOnlineUsernames());
    }

    public Optional<User> findByIdentifier(String identifier) {
        String normalized = IdentifierNormalizer.normalizeIdentifier(identifier);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }

        return userRepository.findByUsernameIgnoreCase(normalized)
                .or(() -> userRepository.findByEmailIgnoreCase(IdentifierNormalizer.normalizeEmail(normalized)));
    }

    public Optional<User> findByIdentifier(String identifier, IdentifierType type) {
        if (type == IdentifierType.EMAIL) {
            return userRepository.findByEmailIgnoreCase(IdentifierNormalizer.normalizeEmail(identifier));
        }
        return Optional.empty();
    }

    private Optional<User> resolveUserForOtpLogin(String identifier, IdentifierType type) {
        String normalized = normalizeByType(identifier, type);
        return findByIdentifier(normalized, type)
                .or(() -> findByIdentifier(normalized));
    }

    public UserDTO convertToUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setBio(user.getBio());
        dto.setGoogleId(user.getGoogleId() != null ? "connected" : null);
        dto.setAuthProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : null);
        return dto;
    }

    private LoginResponseDTO issueLoginResponse(User user) {
        String token = jwtService.generateToken(user);
        UserDTO userDTO = convertToUserDTO(user);
        userDTO.setToken(token);
        return LoginResponseDTO.builder()
                .token(token)
                .userDTO(userDTO)
                .build();
    }

    private String normalizeByType(String identifier, IdentifierType type) {
        return IdentifierNormalizer.normalizeEmail(identifier);
    }

    private boolean hasPassword(User user) {
        return user.getPassword() != null && !user.getPassword().isBlank();
    }
}
