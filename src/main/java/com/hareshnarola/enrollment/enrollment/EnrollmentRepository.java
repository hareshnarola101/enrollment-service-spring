package com.hareshnarola.enrollment.enrollment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByCourseIdAndStudentIdAndStatusIn(Long courseId, Long studentId,
                                                   Collection<EnrollmentStatus> statuses);

    /** Waitlist in arrival order; the id breaks ties between identical timestamps. */
    List<Enrollment> findByCourseIdAndStatusOrderByCreatedAtAscIdAsc(Long courseId, EnrollmentStatus status,
                                                                    Limit limit);

    /**
     * Scalar lookup used before taking the course lock. It deliberately does not load the
     * entity, so the enrollment is read fresh only after the lock is held.
     */
    @Query("select e.courseId from Enrollment e where e.id = :id")
    Optional<Long> findCourseIdById(@Param("id") Long id);

    Page<Enrollment> findByStudentId(Long studentId, Pageable pageable);

    Page<Enrollment> findByCourseId(Long courseId, Pageable pageable);

    Page<Enrollment> findByCourseIdAndStatus(Long courseId, EnrollmentStatus status, Pageable pageable);

    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);
}
