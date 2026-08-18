package com.qms.qms.service;

import com.qms.qms.entity.QaTicket;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.entity.enums.TicketStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class QaTicketSpecifications {

    private QaTicketSpecifications() {
    }

    /** Fetch-joins the *ToOne associations read by QaTicketSummaryResponse, avoiding N+1 on list(). */
    public static Specification<QaTicket> withSummaryAssociations() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class) {
                root.fetch("staff", JoinType.LEFT);
                root.fetch("factory", JoinType.LEFT);
                root.fetch("line", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    public static Specification<QaTicket> factoryId(Long factoryId) {
        return (root, query, cb) -> factoryId == null ? null : cb.equal(root.get("factory").get("id"), factoryId);
    }

    public static Specification<QaTicket> customer(String customer) {
        return (root, query, cb) -> (customer == null || customer.isBlank()) ? null
                : cb.like(cb.lower(root.get("customerName")), "%" + customer.toLowerCase() + "%");
    }

    public static Specification<QaTicket> staffId(Long staffId) {
        return (root, query, cb) -> staffId == null ? null : cb.equal(root.get("staff").get("id"), staffId);
    }

    public static Specification<QaTicket> inspectionStage(InspectionStage inspectionStage) {
        return (root, query, cb) -> inspectionStage == null ? null : cb.equal(root.get("inspectionStage"), inspectionStage);
    }

    public static Specification<QaTicket> status(TicketStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<QaTicket> exported(Boolean exported) {
        return (root, query, cb) -> exported == null ? null : cb.equal(root.get("exported"), exported);
    }

    public static Specification<QaTicket> dateFrom(LocalDate dateFrom) {
        return (root, query, cb) -> dateFrom == null ? null
                : cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay());
    }

    public static Specification<QaTicket> dateTo(LocalDate dateTo) {
        return (root, query, cb) -> dateTo == null ? null
                : cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay());
    }

    /** Keyset cursor: only rows strictly older (lower id) than the last-seen id, for descending-by-id pagination. */
    public static Specification<QaTicket> idBefore(Long cursor) {
        return (root, query, cb) -> cursor == null ? null : cb.lessThan(root.get("id"), cursor);
    }

    /** Fetch-joins the defect/location/defect-item/defect-category graph read by the dashboard aggregation. */
    public static Specification<QaTicket> withDashboardAssociations() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class) {
                var defectsFetch = root.fetch("defects", JoinType.LEFT);
                defectsFetch.fetch("locations", JoinType.LEFT);
                defectsFetch.fetch("defectItem", JoinType.LEFT).fetch("defect", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }
}
