package com.creditx.main.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.creditx.main.model.ProcessedEvent;
import com.creditx.main.repository.ProcessedEventRepository;
import com.creditx.main.service.impl.ProcessedEventServiceImpl;

@ExtendWith(MockitoExtension.class)
class ProcessedEventServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ProcessedEventServiceImpl processedEventService;

    private String eventId;
    private String payloadHash;
    private String status;

    @BeforeEach
    void setUp() {
        eventId = "test-event-123";
        payloadHash = "abc123hash";
        status = "SUCCESS";
    }

    @Test
    void isEventProcessed_returnsTrue_whenEventExists() {
        // Given
        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        // When
        boolean result = processedEventService.isEventProcessed(eventId);

        // Then
        assertThat(result).isTrue();
        verify(processedEventRepository).existsByEventId(eventId);
    }

    @Test
    void isEventProcessed_returnsFalse_whenEventDoesNotExist() {
        // Given
        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        // When
        boolean result = processedEventService.isEventProcessed(eventId);

        // Then
        assertThat(result).isFalse();
        verify(processedEventRepository).existsByEventId(eventId);
    }

    @Test
    void markEventAsProcessed_savesProcessedEvent() {
        // Given
        when(processedEventRepository.save(any(ProcessedEvent.class)))
                .thenReturn(ProcessedEvent.builder().eventId(eventId).build());

        // When
        processedEventService.markEventAsProcessed(eventId, payloadHash, status);

        // Then
        ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(eventCaptor.capture());
        
        ProcessedEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventId()).isEqualTo(eventId);
        assertThat(savedEvent.getPayloadHash()).isEqualTo(payloadHash);
        assertThat(savedEvent.getStatus()).isEqualTo(status);
    }

    @Test
    void isPayloadProcessed_returnsTrue_whenPayloadExists() {
        // Given
        when(processedEventRepository.existsByPayloadHash(payloadHash)).thenReturn(true);

        // When
        boolean result = processedEventService.isPayloadProcessed(payloadHash);

        // Then
        assertThat(result).isTrue();
        verify(processedEventRepository).existsByPayloadHash(payloadHash);
    }

    @Test
    void isPayloadProcessed_returnsFalse_whenPayloadDoesNotExist() {
        // Given
        when(processedEventRepository.existsByPayloadHash(payloadHash)).thenReturn(false);

        // When
        boolean result = processedEventService.isPayloadProcessed(payloadHash);

        // Then
        assertThat(result).isFalse();
        verify(processedEventRepository).existsByPayloadHash(payloadHash);
    }
}
