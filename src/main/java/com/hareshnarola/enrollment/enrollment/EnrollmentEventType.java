package com.hareshnarola.enrollment.enrollment;

/**
 * Event names as they appear on the wire (payload {@code type} field and {@code eventType} header).
 */
public enum EnrollmentEventType {
    CONFIRMED("EnrollmentConfirmed"),
    CANCELLED("EnrollmentCancelled"),
    PROMOTED_FROM_WAITLIST("StudentPromotedFromWaitlist");

    private final String wireName;

    EnrollmentEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
