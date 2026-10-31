package com.hareshnarola.enrollment.course;

/**
 * Published inside the transaction that raised a course's capacity, while the course row
 * is still locked. Listeners run synchronously in that same transaction.
 */
public record CourseCapacityIncreased(Course course) {
}
