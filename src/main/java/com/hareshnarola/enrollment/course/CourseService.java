package com.hareshnarola.enrollment.course;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import com.hareshnarola.enrollment.common.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CourseService(CourseRepository courses, ApplicationEventPublisher events, Clock clock) {
        this.courses = courses;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        String code = request.code().toUpperCase(Locale.ROOT);
        if (courses.existsByCode(code)) {
            throw new BusinessRuleViolationException("A course with code " + code + " already exists");
        }
        Course course = new Course(code, request.title().trim(), request.description(),
                request.capacity(), clock.instant());
        return CourseResponse.from(courses.save(course));
    }

    /**
     * Takes the course lock because a capacity change must see an up-to-date seat count,
     * and growing capacity hands the new seats to waitlisted students in the same transaction.
     */
    @Transactional
    public CourseResponse update(Long id, UpdateCourseRequest request) {
        Course course = courses.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Course", id));
        Instant now = clock.instant();
        course.updateDetails(request.title().trim(), request.description(), now);
        if (course.changeCapacity(request.capacity(), now)) {
            events.publishEvent(new CourseCapacityIncreased(course));
        }
        return CourseResponse.from(course);
    }

    @Transactional(readOnly = true)
    public CourseResponse get(Long id) {
        return courses.findById(id)
                .map(CourseResponse::from)
                .orElseThrow(() -> new NotFoundException("Course", id));
    }

    @Transactional(readOnly = true)
    public Page<CourseResponse> list(Pageable pageable) {
        return courses.findAll(pageable).map(CourseResponse::from);
    }
}
