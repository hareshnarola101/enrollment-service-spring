package com.hareshnarola.enrollment.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("7f3c1f0e-8c1b-4b55-9a77-0f6f3f7b2a10");
    private static final EnrollmentNotificationMessage PROMOTED =
            new EnrollmentNotificationMessage("StudentPromotedFromWaitlist", 100L, 10L, 42L);

    @Mock
    private ProcessedEventStore processedEvents;
    @Mock
    private NotificationRepository notifications;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(processedEvents, notifications, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordsANotificationForANewEvent() {
        when(processedEvents.markProcessed(NotificationService.CONSUMER_NAME, EVENT_ID, NOW)).thenReturn(true);

        boolean handled = service.handle(EVENT_ID, PROMOTED);

        assertThat(handled).isTrue();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getStudentId()).isEqualTo(42L);
        assertThat(saved.getValue().getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getValue().getMessage()).contains("course 10").contains("waitlist");
    }

    @Test
    void skipsAnEventThatWasAlreadyProcessed() {
        when(processedEvents.markProcessed(NotificationService.CONSUMER_NAME, EVENT_ID, NOW)).thenReturn(false);

        boolean handled = service.handle(EVENT_ID, PROMOTED);

        assertThat(handled).isFalse();
        verify(notifications, never()).save(any());
    }
}
