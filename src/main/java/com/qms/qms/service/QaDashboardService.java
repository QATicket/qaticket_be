package com.qms.qms.service;

import com.qms.qms.dto.dashboard.DhuByStageDTO;
import com.qms.qms.dto.dashboard.DhuTimelinePointDTO;
import com.qms.qms.dto.dashboard.ParetoDefectGroupDTO;
import com.qms.qms.dto.dashboard.QaDashboardResponse;
import com.qms.qms.dto.dashboard.StageSummaryDTO;
import com.qms.qms.entity.QaTicket;
import com.qms.qms.entity.QaTicketDefect;
import com.qms.qms.entity.QaTicketDefectLocation;
import com.qms.qms.entity.Staff;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.entity.enums.StaffRole;
import com.qms.qms.entity.enums.TicketStatus;
import com.qms.qms.repository.QaTicketRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QaDashboardService {

    /** Fixed display order/grouping for the dashboard; PREFINAL and FINAL are shown as one combined stage. */
    private static final List<StageBucket> STAGE_BUCKETS = List.of(
            new StageBucket("FIRST_OUTPUT", "First Output", EnumSet.of(InspectionStage.FIRST_OUTPUT)),
            new StageBucket("INLINE", "Inline", EnumSet.of(InspectionStage.INLINE)),
            new StageBucket("ENDLINE", "Endline", EnumSet.of(InspectionStage.ENDLINE)),
            new StageBucket("PREFINAL_FINAL", "Prefinal/Final", EnumSet.of(InspectionStage.PREFINAL, InspectionStage.FINAL)),
            new StageBucket("PACKING", "Packing", EnumSet.of(InspectionStage.PACKING))
    );

    private final QaTicketRepository qaTicketRepository;

    public QaDashboardService(QaTicketRepository qaTicketRepository) {
        this.qaTicketRepository = qaTicketRepository;
    }

    @Transactional(readOnly = true)
    public QaDashboardResponse getDashboard(Long staffId, Long factoryId, InspectionStage inspectionStage, Staff currentStaff) {
        // Non-admins are restricted to their own data regardless of the staffId they pass in,
        // mirroring QaTicketService.list() — same rule, same reason.
        Long effectiveStaffId = currentStaff.getRole() == StaffRole.ADMIN ? staffId : currentStaff.getId();

        Specification<QaTicket> spec = Specification.allOf(
                QaTicketSpecifications.withDashboardAssociations(),
                QaTicketSpecifications.status(TicketStatus.SUBMITTED),
                QaTicketSpecifications.staffId(effectiveStaffId),
                QaTicketSpecifications.factoryId(factoryId),
                QaTicketSpecifications.inspectionStage(inspectionStage)
        );
        List<QaTicket> tickets = qaTicketRepository.findAll(spec);

        return new QaDashboardResponse(
                tickets.size(),
                buildStageSummary(tickets),
                buildDhuTimeline(tickets),
                buildParetoDefectGroups(tickets),
                buildDhuByStage(tickets)
        );
    }

    private List<StageSummaryDTO> buildStageSummary(List<QaTicket> tickets) {
        return STAGE_BUCKETS.stream()
                .map(bucket -> new StageSummaryDTO(bucket.code(), bucket.label(), countInBucket(tickets, bucket)))
                .toList();
    }

    private List<DhuByStageDTO> buildDhuByStage(List<QaTicket> tickets) {
        return STAGE_BUCKETS.stream()
                .map(bucket -> new DhuByStageDTO(bucket.code(), bucket.label(), dhuPercent(ticketsInBucket(tickets, bucket))))
                .toList();
    }

    private List<DhuTimelinePointDTO> buildDhuTimeline(List<QaTicket> tickets) {
        Map<LocalDate, List<QaTicket>> byDate = tickets.stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate()));
        return byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DhuTimelinePointDTO(e.getKey(), dhuPercent(e.getValue())))
                .toList();
    }

    /** Pareto groups by Defect (category), not DefectItem (the specific sub-defect) — see DefectItem.defect. */
    private List<ParetoDefectGroupDTO> buildParetoDefectGroups(List<QaTicket> tickets) {
        Map<String, Long> countByGroup = new LinkedHashMap<>();
        for (QaTicket ticket : tickets) {
            for (QaTicketDefect defect : ticket.getDefects()) {
                String groupName = defect.getDefectItem().getDefect().getNameVi();
                long qty = locationQty(defect);
                countByGroup.merge(groupName, qty, Long::sum);
            }
        }
        long total = countByGroup.values().stream().mapToLong(Long::longValue).sum();

        List<ParetoDefectGroupDTO> result = new ArrayList<>();
        long running = 0;
        for (Map.Entry<String, Long> entry : countByGroup.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).toList()) {
            running += entry.getValue();
            double cumulativePercent = total == 0 ? 0.0 : round1(running * 100.0 / total);
            result.add(new ParetoDefectGroupDTO(entry.getKey(), entry.getValue(), cumulativePercent));
        }
        return result;
    }

    private long countInBucket(List<QaTicket> tickets, StageBucket bucket) {
        return tickets.stream().filter(t -> bucket.stages().contains(t.getInspectionStage())).count();
    }

    private List<QaTicket> ticketsInBucket(List<QaTicket> tickets, StageBucket bucket) {
        return tickets.stream().filter(t -> bucket.stages().contains(t.getInspectionStage())).toList();
    }

    private double dhuPercent(List<QaTicket> tickets) {
        long totalDefects = tickets.stream().flatMap(t -> t.getDefects().stream()).mapToLong(this::locationQty).sum();
        long totalInspected = tickets.stream().mapToLong(QaTicket::getInspectedQty).sum();
        return totalInspected == 0 ? 0.0 : round1(totalDefects * 100.0 / totalInspected);
    }

    private long locationQty(QaTicketDefect defect) {
        return defect.getLocations().stream().mapToLong(QaTicketDefectLocation::getQuantity).sum();
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private record StageBucket(String code, String label, Set<InspectionStage> stages) {
    }
}
