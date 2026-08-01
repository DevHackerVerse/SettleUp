package com.settleup.controller;

import com.settleup.entity.Notification;
import com.settleup.entity.User;
import com.settleup.exception.ResourceNotFoundException;
import com.settleup.exception.UnauthorizedException;
import com.settleup.repository.NotificationRepository;
import com.settleup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST controller for notification endpoints (spec §5.5).
 *
 * GET  /api/v1/notifications?unreadOnly=true   → list notifications (paginated)
 * POST /api/v1/notifications/{id}/read         → mark notification as read
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<NotificationDto>> listNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        PageRequest pageable = PageRequest.of(page, size);

        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(currentUser.getId(), pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);

        return ResponseEntity.ok(notifications.map(NotificationDto::from));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Map<String, Boolean>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        int updated = notificationRepository.markAsRead(id, currentUser.getId());
        if (updated == 0) {
            throw new ResourceNotFoundException("Notification not found or does not belong to you: " + id);
        }
        return ResponseEntity.ok(Map.of("read", true));
    }

    // ── Helper ────────────────────────────────────────────────────────

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }

    /** Response DTO for notifications. */
    public record NotificationDto(
            Long id,
            String type,
            Map<String, Object> payload,
            boolean read,
            LocalDateTime createdAt
    ) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(
                    n.getId(),
                    n.getType().name(),
                    n.getPayloadJson(),
                    n.isRead(),
                    n.getCreatedAt()
            );
        }
    }
}
