package com.qms.qms.dto.ticket;

import com.qms.qms.dto.RefResponse;
import com.qms.qms.entity.QaTicket;
import com.qms.qms.entity.enums.AqlLevel;
import com.qms.qms.entity.enums.InspectionResult;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.entity.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

public record QaTicketResponse(
        Long id,
        String ticketCode,
        RefResponse staff,
        RefResponse factory,
        RefResponse line,
        RefResponse group,
        String poNumber,
        String style,
        String customerName,
        RefResponse garmentType,
        InspectionStage inspectionStage,
        Integer inspectedQty,
        TicketStatus status,
        boolean exported,
        LocalDateTime exportedAt,
        AqlLevel aqlLevel,
        Integer qtySize,
        Integer samplingSize,
        Integer actualMajorDefects,
        Integer actualMinorDefects,
        InspectionResult inspectionResult,
        String specNote,
        String qualityNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<QaTicketDefectResponse> defects,
        List<QaTicketSpecImageResponse> specImages,
        List<QaTicketMeasurementImageResponse> measurementImages
) {
    public static QaTicketResponse from(QaTicket t) {
        return new QaTicketResponse(
                t.getId(),
                t.getTicketCode(),
                new RefResponse(t.getStaff().getId(), t.getStaff().getFullName()),
                new RefResponse(t.getFactory().getId(), t.getFactory().getName()),
                new RefResponse(t.getLine().getId(), t.getLine().getName()),
                t.getGroup() != null ? new RefResponse(t.getGroup().getId(), t.getGroup().getName()) : null,
                t.getPoNumber(),
                t.getStyle(),
                t.getCustomerName(),
                new RefResponse(t.getGarmentType().getId(), t.getGarmentType().getName()),
                t.getInspectionStage(),
                t.getInspectedQty(),
                t.getStatus(),
                t.isExported(),
                t.getExportedAt(),
                t.getAqlLevel(),
                t.getQtySize(),
                t.getSamplingSize(),
                t.getActualMajorDefects(),
                t.getActualMinorDefects(),
                t.getInspectionResult(),
                t.getSpecNote(),
                t.getQualityNote(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getDefects().stream().map(QaTicketDefectResponse::from).toList(),
                t.getSpecImages().stream().map(QaTicketSpecImageResponse::from).toList(),
                t.getMeasurementImages().stream().map(QaTicketMeasurementImageResponse::from).toList()
        );
    }
}
