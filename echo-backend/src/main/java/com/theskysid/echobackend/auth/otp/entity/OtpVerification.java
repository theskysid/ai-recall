package com.theskysid.echobackend.auth.otp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "otp_verifications")
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String identifier;      // email or phone

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdentifierType type;    // EMAIL or PHONE

    @Column(nullable = false)
    private String otpCode;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Column(nullable = false)
    private int requestCount;

    @Column(nullable = false)
    private LocalDateTime windowStart;

    public enum IdentifierType { EMAIL, PHONE }
}