package com.placefy.application.port.in;

import com.placefy.domain.user.UserId;

/**
 * Aggregate figures for the admin dashboard.
 *
 * <p>Counts only. Per ADR-007 an administrator sees totals across students and never one
 * student's individual data, so nothing returned here can identify anybody.
 */
public interface GetAdminOverview {

    AdminOverview handle(UserId requester);

    record AdminOverview(long studentCount) {}
}
