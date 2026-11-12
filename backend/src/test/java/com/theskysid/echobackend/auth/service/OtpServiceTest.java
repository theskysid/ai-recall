package com.theskysid.echobackend.auth.service;

import com.theskysid.echobackend.auth.otp.entity.OtpVerification;
import com.theskysid.echobackend.auth.otp.entity.OtpVerification.IdentifierType;
import com.theskysid.echobackend.auth.otp.repository.OtpVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {
    @Mock OtpVerificationRepository repository;
    @InjectMocks OtpService service;
    OtpVerification record;

    @BeforeEach
    void setup() {
        record = new OtpVerification();
        record.setIdentifier("alice@example.com");
        record.setType(IdentifierType.EMAIL);
        record.setOtpCode("123456");
        record.setExpiry(LocalDateTime.now().plusMinutes(5));
        record.setWindowStart(LocalDateTime.now());
        record.setRequestCount(1);
        when(repository.findByIdentifierAndType(record.getIdentifier(), IdentifierType.EMAIL)).thenReturn(Optional.of(record));
    }

    @Test
    void fiveWrongGuessesLockTheCodeEvenWhenNextGuessIsCorrect() {
        for (int i = 0; i < 5; i++) {
            assertThrows(OtpService.OtpVerificationException.class, () -> service.verifyOtp(record.getIdentifier(), "wrong"));
        }
        assertEquals(5, record.getFailedAttempts());
        assertThrows(OtpService.OtpVerificationException.class, () -> service.verifyOtp(record.getIdentifier(), "123456"));
        verify(repository, times(5)).save(record);
        verify(repository, never()).delete(any());
    }

    @Test
    void successConsumesTheCode() {
        service.verifyOtp(record.getIdentifier(), "123456");
        verify(repository).delete(record);
    }

    @Test
    void resendResetsGuessesButPreservesSendLimit() {
        record.setFailedAttempts(5);
        service.createForEmail(record.getIdentifier());
        assertEquals(0, record.getFailedAttempts());
        assertEquals(2, record.getRequestCount());
        service.createForEmail(record.getIdentifier());
        assertThrows(RuntimeException.class, () -> service.createForEmail(record.getIdentifier()));
        assertEquals(3, record.getRequestCount());
    }

    @Test
    void expiryDoesNotEraseTheSendRateLimit() {
        record.setExpiry(LocalDateTime.now().minusMinutes(1));
        record.setRequestCount(3);
        assertThrows(OtpService.OtpVerificationException.class, () -> service.verifyOtp(record.getIdentifier(), "123456"));
        assertThrows(RuntimeException.class, () -> service.createForEmail(record.getIdentifier()));
        verify(repository, never()).delete(any());
    }
}
