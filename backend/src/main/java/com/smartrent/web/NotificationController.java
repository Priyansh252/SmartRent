package com.smartrent.web;

import com.smartrent.dto.Dtos.NotificationDto;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.messaging.NotificationService;
import com.smartrent.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** The caller's own notifications only (the user id comes from the session, never from the request). */
    @GetMapping("/api/notifications/mine")
    public PageResponse<NotificationDto> mine(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @AuthenticationPrincipal AppUserDetails me) {
        return notifications.mine(me.getId(), page, size);
    }
}
