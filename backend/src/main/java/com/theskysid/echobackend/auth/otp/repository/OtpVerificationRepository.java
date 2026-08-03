package com.theskysid.echobackend.auth.otp.repository;

import com.theskysid.echobackend.auth.otp.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findByIdentifierAndType(String identifier, OtpVerification.IdentifierType type);
}