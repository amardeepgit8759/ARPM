package com.placefy.web.admin;

import com.placefy.application.port.in.GetAdminOverview;
import com.placefy.domain.user.UserId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator endpoints. Aggregates only, per ADR-007. */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

    private final GetAdminOverview getAdminOverview;

    AdminController(GetAdminOverview getAdminOverview) {
        this.getAdminOverview = getAdminOverview;
    }

    @GetMapping("/overview")
    OverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        GetAdminOverview.AdminOverview overview = getAdminOverview.handle(UserId.parse(jwt.getSubject()));
        return new OverviewResponse(overview.studentCount());
    }

    record OverviewResponse(long studentCount) {}
}
