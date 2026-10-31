package com.hareshnarola.enrollment.course;

import java.time.Instant;

import com.hareshnarola.enrollment.common.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A course and its seat counter. {@code seatsTaken} is the number of CONFIRMED enrollments;
 * keeping it on the course row means the row lock taken before any seat change also
 * protects the counter, and the capacity check is a field comparison instead of a COUNT(*).
 */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "seats_taken", nullable = false)
    private int seatsTaken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {
        // for JPA
    }

    public Course(String code, String title, String description, int capacity, Instant now) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.code = code;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean hasFreeSeat() {
        return seatsTaken < capacity;
    }

    public int freeSeats() {
        return capacity - seatsTaken;
    }

    public void takeSeat(Instant now) {
        if (!hasFreeSeat()) {
            throw new IllegalStateException("Course " + code + " has no free seat");
        }
        seatsTaken++;
        updatedAt = now;
    }

    public void releaseSeat(Instant now) {
        if (seatsTaken == 0) {
            throw new IllegalStateException("Course " + code + " has no taken seat to release");
        }
        seatsTaken--;
        updatedAt = now;
    }

    public void updateDetails(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    /**
     * @return true if capacity grew, meaning waitlisted students may now get a seat
     */
    public boolean changeCapacity(int newCapacity, Instant now) {
        if (newCapacity < seatsTaken) {
            throw new BusinessRuleViolationException(
                    "Capacity cannot be lower than the %d seats already confirmed".formatted(seatsTaken));
        }
        boolean grew = newCapacity > capacity;
        this.capacity = newCapacity;
        this.updatedAt = now;
        return grew;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
