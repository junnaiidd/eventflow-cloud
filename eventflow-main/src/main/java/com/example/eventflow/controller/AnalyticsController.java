package com.example.eventflow.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.dto.AnalyticsOverviewDTO;
import com.example.eventflow.security.AppUserDetails;
import com.example.eventflow.service.AnalyticsService;

/** Registration and attendance analytics (organizer/admin only, enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public AnalyticsOverviewDTO overview(@AuthenticationPrincipal AppUserDetails me) {
        return analytics.overview(me);
    }
}
