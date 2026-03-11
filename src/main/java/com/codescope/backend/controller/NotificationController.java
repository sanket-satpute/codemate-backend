package com.codescope.backend.controller;

import com.codescope.backend.dto.BaseResponse;
import com.codescope.backend.exception.CustomException;
import com.codescope.backend.model.Notification;
import com.codescope.backend.model.User;
import com.codescope.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationRepository notificationRepository;

    /**
     * GET /api/notifications — Fetch all notifications for the current user.
     * Used by frontend NotificationsService.getNotifications().
     */
    @GetMapping
    public Mono<ResponseEntity<BaseResponse<List<Notification>>>> getNotifications() {
        return getCurrentUserId()
                .flatMap(userId -> notificationRepository.findByUserIdOrderByTimestampDesc(userId)
                        .collectList()
                        .map(notifications -> ResponseEntity.ok(
                                BaseResponse.success(notifications, "Notifications retrieved successfully"))))
                .onErrorResume(e -> {
                    log.error("Error fetching notifications: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to retrieve notifications: " + e.getMessage())));
                });
    }

    /**
     * POST /api/notifications/mark-read — Mark a specific notification as read.
     * Used by frontend NotificationsService.markAsRead().
     */
    @PostMapping("/mark-read")
    public Mono<ResponseEntity<BaseResponse<Void>>> markAsRead(@RequestBody Map<String, String> body) {
        String notificationId = body.get("notificationId");
        if (notificationId == null || notificationId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(BaseResponse.error("notificationId is required")));
        }
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(new CustomException("Notification not found", HttpStatus.NOT_FOUND)))
                .flatMap(notification -> {
                    notification.setRead(true);
                    return notificationRepository.save(notification);
                })
                .map(saved -> ResponseEntity.ok(BaseResponse.<Void>success(null, "Notification marked as read")))
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error marking notification as read: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.error("Failed to mark notification as read: " + e.getMessage())));
                });
    }

    /**
     * POST /api/notifications/mark-all-read — Mark all notifications as read for the current user.
     */
    @PostMapping("/mark-all-read")
    public Mono<ResponseEntity<BaseResponse<Void>>> markAllAsRead() {
        return getCurrentUserId()
                .flatMap(userId -> notificationRepository.findByUserIdAndReadFalse(userId)
                        .flatMap(notification -> {
                            notification.setRead(true);
                            return notificationRepository.save(notification);
                        })
                        .then(Mono.just(ResponseEntity.ok(
                                BaseResponse.<Void>success(null, "All notifications marked as read")))))
                .onErrorResume(e -> {
                    log.error("Error marking all as read: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<Void>error("Failed to mark all as read")));
                });
    }

    /**
     * DELETE /api/notifications/{id} — Delete a single notification.
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<BaseResponse<Void>>> deleteNotification(@PathVariable String id) {
        return notificationRepository.findById(id)
                .switchIfEmpty(Mono.error(new CustomException("Notification not found", HttpStatus.NOT_FOUND)))
                .flatMap(notification -> notificationRepository.delete(notification))
                .then(Mono.just(ResponseEntity.ok(
                        BaseResponse.<Void>success(null, "Notification deleted"))))
                .onErrorResume(CustomException.class, e -> Mono.just(ResponseEntity.status(e.getStatus())
                        .body(BaseResponse.<Void>error(e.getMessage()))))
                .onErrorResume(e -> {
                    log.error("Error deleting notification: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<Void>error("Failed to delete notification")));
                });
    }

    /**
     * DELETE /api/notifications/clear — Clear all notifications for the current
     * user.
     * Used by frontend NotificationsService.clearAllNotifications().
     */
    @DeleteMapping("/clear")
    public Mono<ResponseEntity<BaseResponse<Void>>> clearAllNotifications() {
        return getCurrentUserId()
                .flatMap(userId -> notificationRepository.deleteAllByUserId(userId))
                .then(Mono.just(ResponseEntity.ok(BaseResponse.<Void>success(null, "All notifications cleared"))))
                .onErrorResume(e -> {
                    log.error("Error clearing notifications: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BaseResponse.<Void>error("Failed to clear notifications: " + e.getMessage())));
                });
    }

    /**
     * Helper to extract the current user's ID from the reactive security context.
     */
    private Mono<String> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(User.class)
                .map(User::getId)
                .switchIfEmpty(Mono.error(new CustomException("No authenticated user found", HttpStatus.UNAUTHORIZED)));
    }
}
