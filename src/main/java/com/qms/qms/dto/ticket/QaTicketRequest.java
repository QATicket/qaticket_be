package com.qms.qms.dto.ticket;

import com.qms.qms.entity.enums.AqlLevel;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.entity.enums.TicketStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QaTicketRequest(
        @NotNull Long staffId,
        @NotNull Long factoryId,
        @NotNull Long lineId,
        Long groupId,
        @NotNull InspectionStage inspectionStage,
        String poNumber,
        String style,
        @NotNull @Min(1) Integer inspectedQty,
        @NotBlank String customerName,
        String color,
        @NotNull Long garmentTypeId,
        @NotNull TicketStatus status,
        AqlLevel aqlLevel,
        @Min(1) Integer qtySize,
        @Min(0) Integer actualMajorDefects,
        @Min(0) Integer actualMinorDefects,
        String specNote,
        String qualityNote,
        @Valid List<QaTicketDefectRequest> defects,
        @Valid List<QaTicketSpecImageRequest> specImages,
        @Valid List<QaTicketMeasurementImageRequest> measurementImages
) {
}
