package com.qms.qms.controller;

import com.qms.qms.dto.dashboard.QaDashboardResponse;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.security.StaffPrincipal;
import com.qms.qms.service.QaDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
public class QaDashboardController {

    private final QaDashboardService qaDashboardService;

    public QaDashboardController(QaDashboardService qaDashboardService) {
        this.qaDashboardService = qaDashboardService;
    }

    /**
     * Aggregated dashboard (stage counts, DHU timeline, defect Pareto, DHU by stage) across every
     * SUBMITTED ticket currently in the database. {@code factoryId} and {@code inspectionStage} are
     * free filters for everyone. {@code staffId} only applies to admins — non-admins always see just
     * their own data, no matter what (or whether) they pass for {@code staffId}.
     */
    @GetMapping("/dashboard")
    public QaDashboardResponse dashboard(@RequestParam(required = false) Long staffId,
                                          @RequestParam(required = false) Long factoryId,
                                          @RequestParam(required = false) InspectionStage inspectionStage,
                                          @AuthenticationPrincipal StaffPrincipal principal) {
        return qaDashboardService.getDashboard(staffId, factoryId, inspectionStage, principal.getStaff());
    }
}
