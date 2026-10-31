package com.hareshnarola.enrollment.enrollment;

import java.net.URI;

import com.hareshnarola.enrollment.auth.CurrentUser;
import com.hareshnarola.enrollment.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Enrollments")
class EnrollmentController {

    private final EnrollmentService enrollmentService;

    EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/api/courses/{courseId}/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Enroll in a course",
            description = "Returns CONFIRMED if a seat was free, WAITLISTED otherwise")
    ResponseEntity<EnrollmentResponse> enroll(@PathVariable Long courseId,
                                              @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        EnrollmentResponse enrollment = enrollmentService.enroll(courseId, CurrentUser.from(jwt));
        return ResponseEntity.created(URI.create("/api/enrollments/" + enrollment.id())).body(enrollment);
    }

    @GetMapping("/api/courses/{courseId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List a course's enrollments (admin)",
            description = "Optionally filter by status, e.g. status=WAITLISTED")
    PageResponse<EnrollmentResponse> listForCourse(
            @PathVariable Long courseId,
            @RequestParam(required = false) EnrollmentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(enrollmentService.listForCourse(courseId, status, pageable));
    }

    @GetMapping("/api/enrollments/me")
    @Operation(summary = "List my enrollments")
    PageResponse<EnrollmentResponse> mine(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(enrollmentService.listForStudent(CurrentUser.from(jwt).id(), pageable));
    }

    @GetMapping("/api/enrollments/{id}")
    @Operation(summary = "Get an enrollment", description = "Students can only see their own")
    EnrollmentResponse get(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.get(id, CurrentUser.from(jwt));
    }

    @PostMapping("/api/enrollments/{id}/cancel")
    @Operation(summary = "Cancel an enrollment",
            description = "Students can cancel their own; admins can cancel any. "
                    + "A freed seat goes to the next waitlisted student.")
    EnrollmentResponse cancel(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return enrollmentService.cancel(id, CurrentUser.from(jwt));
    }
}
