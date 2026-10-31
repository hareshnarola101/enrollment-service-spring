package com.hareshnarola.enrollment.course;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * Loads the course with {@code SELECT ... FOR UPDATE}. Every operation that changes
     * seats or the waitlist of a course goes through this first, so those operations run
     * one at a time per course while different courses stay fully concurrent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCode(String code);
}
