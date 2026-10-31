package com.hareshnarola.enrollment.notification;

import com.hareshnarola.enrollment.auth.CurrentUser;
import com.hareshnarola.enrollment.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Notifications")
class NotificationController {

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/notifications/me")
    @Operation(summary = "List my notifications",
            description = "Written by the Kafka consumer, so they appear shortly after the enrollment change")
    PageResponse<NotificationResponse> mine(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(notificationService.listForStudent(CurrentUser.from(jwt).id(), pageable));
    }
}
