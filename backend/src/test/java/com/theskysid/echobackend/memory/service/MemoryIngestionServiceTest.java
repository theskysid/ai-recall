package com.theskysid.echobackend.memory.service;

import com.theskysid.echobackend.channel.entity.Channel;
import com.theskysid.echobackend.channel.entity.ChannelMessage;
import com.theskysid.echobackend.channel.repository.ChannelRepository;
import com.theskysid.echobackend.memory.repository.MemoryVectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryIngestionServiceTest {
    @Mock EmbeddingService embeddings;
    @Mock MemoryVectorRepository memories;
    @Mock DecisionService decisions;
    @Mock ChannelRepository channels;
    @Mock PlatformTransactionManager transactions;
    @InjectMocks MemoryIngestionService ingestion;

    @Test
    void queuedIngestionCannotRecreateMemoriesAfterChannelDeletion() {
        when(embeddings.embed("queued message")).thenReturn(new float[384]);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(channels.findByIdForShare(42L)).thenReturn(Optional.empty());
        ingestion.ingestMessage(ChannelMessage.builder().id(1L).content("queued message")
                .channel(Channel.builder().id(42L).build()).build());
        verify(channels).findByIdForShare(42L);
        verify(decisions).prepareSupersession(any());
        verify(decisions, never()).applySupersession(any(), any());
        verifyNoInteractions(memories);
        verify(transactions).commit(any());
    }

    @Test
    void modelPreparationFinishesBeforeOpeningTheWriteTransaction() {
        when(embeddings.embed("message")).thenReturn(new float[384]);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(channels.findByIdForShare(42L)).thenReturn(Optional.of(Channel.builder().id(42L).build()));
        ingestion.ingestMessage(ChannelMessage.builder().id(1L).content("message")
                .channel(Channel.builder().id(42L).build()).build());
        var order = inOrder(decisions, transactions, channels);
        order.verify(decisions).prepareSupersession(any());
        order.verify(transactions).getTransaction(any());
        order.verify(channels).findByIdForShare(42L);
        order.verify(decisions).applySupersession(any(), any());
        order.verify(transactions).commit(any());
    }
}
