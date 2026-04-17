package com.adewole.nairacore.notifications.controller;

import com.adewole.nairacore.notifications.dto.NotificationResponse;
import com.adewole.nairacore.notifications.service.NotificationService;
import com.adewole.nairacore.shared.config.UserPrincipal;
import com.adewole.nairacore.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Notifications", description = "Endpoints for notification history")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // ─── Get My Notifications ─────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications() {
        UUID userId = extractUserId();
        List<NotificationResponse> response = notificationService
                .getMyNotifications(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Notifications retrieved successfully", response)
        );
    }

    // ─── Get Notifications By Transaction Reference ───────────────────

    @GetMapping("/transaction/{reference}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByReference(
            @PathVariable String reference
    ) {
        List<NotificationResponse> response = notificationService
                .getByTransactionReference(reference);
        return ResponseEntity.ok(
                ApiResponse.success("Notifications retrieved successfully", response)
        );
    }

    // ─── Private Helpers ─────────────────────────────────────────────

    private UUID extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getUserId();
    }
}