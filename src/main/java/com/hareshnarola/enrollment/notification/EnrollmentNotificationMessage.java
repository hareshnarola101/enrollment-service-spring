package com.hareshnarola.enrollment.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The consumer's own view of an enrollment event. It does not reuse the producer's class
 * on purpose: in a real deployment this consumer would be a separate service, and it should
 * only depend on the fields it reads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EnrollmentNotificationMessage(String type, Long enrollmentId, Long courseId, Long studentId) {
}
