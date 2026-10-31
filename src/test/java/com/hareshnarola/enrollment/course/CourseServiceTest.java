package com.hareshnarola.enrollment.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import com.hareshnarola.enrollment.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private CourseRepository courses;
    @Mock
    private ApplicationEventPublisher events;

    private CourseService service;

    @BeforeEach
    void setUp() {
        service = new CourseService(courses, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCourseWithUppercaseCode() {
        when(courses.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponse response = service.create(new CreateCourseRequest("java-101", " Java Basics ", null, 30));

        assertThat(response.code()).isEqualTo("JAVA-101");
        assertThat(response.title()).isEqualTo("Java Basics");
        assertThat(response.seatsAvailable()).isEqualTo(30);
    }

    @Test
    void rejectsDuplicateCode() {
        when(courses.existsByCode("JAVA-101")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCourseRequest("JAVA-101", "Java", null, 30)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(courses, never()).save(any());
    }

    @Test
    void raisingCapacityAnnouncesTheNewSeats() {
        Course course = existingCourse(2);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        CourseResponse response = service.update(1L, new UpdateCourseRequest("Java", "updated", 4));

        assertThat(response.capacity()).isEqualTo(4);
        ArgumentCaptor<CourseCapacityIncreased> event = ArgumentCaptor.forClass(CourseCapacityIncreased.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().course()).isSameAs(course);
        verify(courses, never()).findById(anyLong());
    }

    @Test
    void editingDetailsWithoutGrowingCapacityPublishesNothing() {
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(existingCourse(2)));

        service.update(1L, new UpdateCourseRequest("Java 21", null, 2));

        verifyNoInteractions(events);
    }

    @Test
    void cannotShrinkBelowConfirmedSeats() {
        Course course = existingCourse(3);
        course.takeSeat(NOW);
        course.takeSeat(NOW);
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.update(1L, new UpdateCourseRequest("Java", null, 1)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(events);
    }

    @Test
    void updateOfUnknownCourseIsNotFound() {
        when(courses.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new UpdateCourseRequest("Java", null, 1)))
                .isInstanceOf(NotFoundException.class);
    }

    private static Course existingCourse(int capacity) {
        Course course = new Course("JAVA-101", "Java", null, capacity, NOW);
        ReflectionTestUtils.setField(course, "id", 1L);
        return course;
    }
}
