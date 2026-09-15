package com.qms.qms.dto.ticket;

import com.qms.qms.entity.QaTicket;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.entity.enums.TicketStatus;

import java.time.LocalDateTime;

public record QaTicketSummaryResponse(
        Long id,
        String ticketCode,
        String staffName,
        String factoryName,
        String lineName,
        String customerName,
        String color,
        InspectionStage inspectionStage,
        Integer inspectedQty,
        TicketStatus status,
        boolean exported,
        LocalDateTime exportedAt,
        LocalDateTime createdAt
) {
    public static QaTicketSummaryResponse from(QaTicket t) {
        return new QaTicketSummaryResponse(
                t.getId(),
                t.getTicketCode(),
                t.getStaff().getFullName(),
                t.getFactory().getName(),
                t.getLine().getName(),
                t.getCustomerName(),
                t.getColor(),
                t.getInspectionStage(),
                t.getInspectedQty(),
                t.getStatus(),
                t.isExported(),
                t.getExportedAt(),
                t.getCreatedAt()
        );
    }
}
