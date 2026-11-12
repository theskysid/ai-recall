package com.theskysid.echobackend.auth.otp.repository;

import com.theskysid.echobackend.auth.otp.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findByIdentifierAndType(String identifier, OtpVerification.IdentifierType type);
}
