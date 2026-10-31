package com.hareshnarola.enrollment.course;

import java.time.Instant;

public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        int capacity,
        int seatsTaken,
        int seatsAvailable,
        Instant createdAt,
        Instant updatedAt) {

    static CourseResponse from(Course course) {
        return new CourseResponse(course.getId(), course.getCode(), course.getTitle(), course.getDescription(),
                course.getCapacity(), course.getSeatsTaken(), course.freeSeats(),
                course.getCreatedAt(), course.getUpdatedAt());
    }
}
