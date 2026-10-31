package com.hareshnarola.enrollment.enrollment;

import java.util.EnumSet;
import java.util.Set;

/**
 * Enrollment lifecycle. PENDING is the state of a request before a seat decision is made;
 * today that decision happens in the same transaction, so PENDING is never committed, but
 * it gives a payment or approval step a place to live without changing the model.
 */
public enum EnrollmentStatus {
    PENDING,
    CONFIRMED,
    WAITLISTED,
    CANCELLED;

    /** Statuses that count as "the student is in this course or queued for it". */
    public static final Set<EnrollmentStatus> ACTIVE = EnumSet.of(PENDING, CONFIRMED, WAITLISTED);

    public boolean canTransitionTo(EnrollmentStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == WAITLISTED || target == CANCELLED;
            case WAITLISTED -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }
}
