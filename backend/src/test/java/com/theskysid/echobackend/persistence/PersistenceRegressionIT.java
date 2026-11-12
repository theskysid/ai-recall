package com.theskysid.echobackend.persistence;

import com.theskysid.echobackend.auth.otp.entity.OtpVerification;
import com.theskysid.echobackend.auth.otp.repository.OtpVerificationRepository;
import com.theskysid.echobackend.auth.service.OtpService;
import com.theskysid.echobackend.call.entity.CallTranscript;
import com.theskysid.echobackend.call.repository.CallTranscriptRepository;
import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMessage;
import com.theskysid.echobackend.channel.repository.*;
import com.theskysid.echobackend.channel.service.ChannelService;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import com.theskysid.echobackend.memory.service.MemoryIngestionService;
import com.theskysid.echobackend.user.entity.AuthProvider;
import com.theskysid.echobackend.user.entity.User;
import com.theskysid.echobackend.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt in with RECALL_TEST_DB_URL pointing to a disposable recall_regression database. */
@SpringJUnitConfig(PersistenceRegressionIT.Config.class)
@EnabledIfEnvironmentVariable(named = "RECALL_TEST_DB_URL", matches = ".+")
@ActiveProfiles("persistence-regression")
class PersistenceRegressionIT {
    @Configuration
    @Profile("persistence-regression")
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {ChannelRepository.class, CallTranscriptRepository.class,
            OtpVerificationRepository.class, UserRepository.class})
    @Import({ChannelService.class, OtpService.class})
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("RECALL_TEST_DB_URL");
            if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/recall_regression(\\?.*)?")) {
                throw new IllegalArgumentException("Tests require a local disposable recall_regression database");
            }
            return new DriverManagerDataSource(url);
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(User.class.getPackageName(), Channel.class.getPackageName(),
                    CallTranscript.class.getPackageName(), OtpVerification.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean MemoryVectorRepository memories() { return mock(MemoryVectorRepository.class); }
    }

    @MockBean MemoryIngestionService ingestion;
    @Autowired OtpService otp;
    @Autowired OtpVerificationRepository codes;
    @Autowired ChannelService channelService;
    @Autowired ChannelRepository channels;
    @Autowired ChannelMembershipRepository memberships;
    @Autowired ChannelMessageRepository messages;
    @Autowired CallTranscriptRepository transcripts;
    @Autowired UserRepository users;
    @Autowired MemoryVectorRepository memories;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void failedVerificationCommitsAndSuccessfulVerificationCannotBeReplayed() {
        String email = UUID.randomUUID() + "@example.com";
        String code = otp.createForEmail(email);
        assertThrows(OtpService.OtpVerificationException.class, () -> otp.verifyOtp(email, "wrong"));
        assertEquals(1, failedAttempts(email));
        otp.verifyOtp(email, code);
        assertThrows(OtpService.OtpVerificationException.class, () -> otp.verifyOtp(email, code));
    }

    @Test
    void parallelGuessesCannotBypassTheFiveAttemptLimit() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String code = otp.createForEmail(email);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> guesses = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> (Callable<Void>) () -> {
                        assertThrows(OtpService.OtpVerificationException.class, () -> otp.verifyOtp(email, "wrong"));
                        return null;
                    }).toList();
            for (var result : executor.invokeAll(guesses)) result.get(10, TimeUnit.SECONDS);
        }
        assertEquals(5, failedAttempts(email));
        assertThrows(OtpService.OtpVerificationException.class, () -> otp.verifyOtp(email, code));
    }

    @Test
    void lastMemberCanLeaveAChannelWithMessagesAndTranscripts() {
        User owner = users.save(User.builder().username("owner").authProvider(AuthProvider.EMAIL).build());
        Channel channel = channelService.createChannel(owner, "history", "test");
        messages.save(ChannelMessage.builder().channel(channel).sender(owner).content("message")
                .type(ChannelMessage.MessageType.CHAT).build());
        transcripts.save(CallTranscript.builder().channel(channel).fullTranscript("transcript").build());
        channelService.leaveChannel(owner, channel.getId());
        assertFalse(channels.existsById(channel.getId()));
        assertEquals(0, messages.count());
        assertEquals(0, transcripts.count());
        assertEquals(0, memberships.count());
        verify(memories).deleteAllByChannelId(channel.getId());
    }

    private int failedAttempts(String email) {
        return new TransactionTemplate(transactions).execute(status -> codes
                .findByIdentifierAndType(email, OtpVerification.IdentifierType.EMAIL).orElseThrow().getFailedAttempts());
    }
}
