package com.hareshnarola.enrollment.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByStudentId(Long studentId, Pageable pageable);

    List<Notification> findByEventId(UUID eventId);

    List<Notification> findByEnrollmentIdOrderById(Long enrollmentId);
}
