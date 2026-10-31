package com.hareshnarola.enrollment.common;

/**
 * A request that is well-formed but conflicts with the current state of the system,
 * e.g. enrolling twice or shrinking a course below its confirmed seats. Mapped to 409.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
