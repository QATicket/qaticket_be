package com.qms.qms.service;

import com.qms.qms.dto.CursorPageResponse;
import com.qms.qms.dto.ticket.*;
import com.qms.qms.entity.*;
import com.qms.qms.entity.enums.AqlLevel;
import com.qms.qms.entity.enums.InspectionResult;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.entity.enums.Severity;
import com.qms.qms.entity.enums.StaffRole;
import com.qms.qms.entity.enums.TicketStatus;
import com.qms.qms.exception.InvalidTicketStateException;
import com.qms.qms.exception.ResourceNotFoundException;
import com.qms.qms.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class QaTicketService {

    private final QaTicketRepository qaTicketRepository;
    private final StaffRepository staffRepository;
    private final FactoryRepository factoryRepository;
    private final LineRepository lineRepository;
    private final ProductionGroupRepository productionGroupRepository;
    private final GarmentTypeRepository garmentTypeRepository;
    private final GarmentLocationRepository garmentLocationRepository;
    private final DefectItemRepository defectItemRepository;
    private final TicketCodeGenerator ticketCodeGenerator;
    private final AqlSamplingService aqlSamplingService;

    public QaTicketService(QaTicketRepository qaTicketRepository,
                            StaffRepository staffRepository,
                            FactoryRepository factoryRepository,
                            LineRepository lineRepository,
                            ProductionGroupRepository productionGroupRepository,
                            GarmentTypeRepository garmentTypeRepository,
                            GarmentLocationRepository garmentLocationRepository,
                            DefectItemRepository defectItemRepository,
                            TicketCodeGenerator ticketCodeGenerator,
                            AqlSamplingService aqlSamplingService) {
        this.qaTicketRepository = qaTicketRepository;
        this.staffRepository = staffRepository;
        this.factoryRepository = factoryRepository;
        this.lineRepository = lineRepository;
        this.productionGroupRepository = productionGroupRepository;
        this.garmentTypeRepository = garmentTypeRepository;
        this.garmentLocationRepository = garmentLocationRepository;
        this.defectItemRepository = defectItemRepository;
        this.ticketCodeGenerator = ticketCodeGenerator;
        this.aqlSamplingService = aqlSamplingService;
    }

    public QaTicketResponse create(QaTicketRequest request) {
        QaTicket ticket = new QaTicket();
        ticket.setTicketCode(ticketCodeGenerator.generate(qaTicketRepository.nextTicketSequence()));
        applyScalarFields(ticket, request);
        applyAqlSampling(ticket, request);
        rebuildDefects(ticket, request);
        rebuildSpecImages(ticket, request);
        rebuildMeasurementImages(ticket, request);

        QaTicket saved = qaTicketRepository.save(ticket);
        return QaTicketResponse.from(saved);
    }

    public QaTicketResponse update(Long id, QaTicketRequest request) {
        QaTicket ticket = qaTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QA ticket not found: " + id));

        applyScalarFields(ticket, request);
        applyAqlSampling(ticket, request);
        rebuildDefects(ticket, request);
        rebuildSpecImages(ticket, request);
        rebuildMeasurementImages(ticket, request);

        QaTicket saved = qaTicketRepository.save(ticket);
        return QaTicketResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public QaTicketResponse getById(Long id, Staff currentStaff) {
        QaTicket ticket = qaTicketRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QA ticket not found: " + id));
        // Non-admins can't view another staff's ticket by id; report it as not found rather than
        // forbidden so we don't confirm the ticket's existence to someone who can't see it.
        boolean isOwner = ticket.getStaff().getId().equals(currentStaff.getId());
        if (currentStaff.getRole() != StaffRole.ADMIN && !isOwner) {
            throw new ResourceNotFoundException("QA ticket not found: " + id);
        }
        return QaTicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<QaTicketSummaryResponse> list(Long factoryId, String customer, Long staffId, TicketStatus status,
                                                              Boolean exported, LocalDate dateFrom, LocalDate dateTo,
                                                              Long cursor, int size, Staff currentStaff) {
        // Non-admins are restricted to their own tickets regardless of the staffId they pass in,
        // so QA_INSPECTOR/QA_LEAD can't view someone else's tickets by guessing their staffId.
        Long effectiveStaffId = currentStaff.getRole() == StaffRole.ADMIN ? staffId : currentStaff.getId();

        Specification<QaTicket> spec = Specification.allOf(
                QaTicketSpecifications.withSummaryAssociations(),
                QaTicketSpecifications.factoryId(factoryId),
                QaTicketSpecifications.customer(customer),
                QaTicketSpecifications.staffId(effectiveStaffId),
                QaTicketSpecifications.status(status),
                QaTicketSpecifications.exported(exported),
                QaTicketSpecifications.dateFrom(dateFrom),
                QaTicketSpecifications.dateTo(dateTo),
                QaTicketSpecifications.idBefore(cursor)
        );
        // Keyset pagination ordered by id DESC: always fetches page 0 (LIMIT only, no OFFSET),
        // so performance stays constant regardless of how deep the client pages.
        var pageable = PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "id"));
        List<QaTicket> rows = qaTicketRepository.findAll(spec, pageable).getContent();

        boolean hasNext = rows.size() > size;
        List<QaTicket> page = hasNext ? rows.subList(0, size) : rows;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new CursorPageResponse<>(page.stream().map(QaTicketSummaryResponse::from).toList(), nextCursor, hasNext);
    }

    public void delete(Long id, Staff currentStaff) {
        QaTicket ticket = qaTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QA ticket not found: " + id));
        // Admins can delete any ticket regardless of status or owner. Non-admins may only delete
        // their own DRAFT tickets; a non-owner gets 404 (mirrors getById) rather than 403 so we
        // don't confirm the ticket's existence, and a submitted ticket of their own gets 409.
        if (currentStaff.getRole() != StaffRole.ADMIN) {
            boolean isOwner = ticket.getStaff().getId().equals(currentStaff.getId());
            if (!isOwner) {
                throw new ResourceNotFoundException("QA ticket not found: " + id);
            }
            if (ticket.getStatus() != TicketStatus.DRAFT) {
                throw new InvalidTicketStateException("Only DRAFT tickets can be deleted: " + id);
            }
        }
        qaTicketRepository.delete(ticket);
    }

    public QaTicketResponse markExported(Long id) {
        QaTicket ticket = qaTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QA ticket not found: " + id));
        ticket.setExported(true);
        ticket.setExportedAt(LocalDateTime.now());
        return QaTicketResponse.from(qaTicketRepository.save(ticket));
    }

    public QaTicketResponse unmarkExported(Long id) {
        QaTicket ticket = qaTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QA ticket not found: " + id));
        ticket.setExported(false);
        ticket.setExportedAt(null);
        return QaTicketResponse.from(qaTicketRepository.save(ticket));
    }

    private void applyScalarFields(QaTicket ticket, QaTicketRequest request) {
        ticket.setStaff(getRef(staffRepository, request.staffId(), "Staff"));
        ticket.setFactory(getRef(factoryRepository, request.factoryId(), "Factory"));
        ticket.setLine(getRef(lineRepository, request.lineId(), "Line"));
        ticket.setGroup(request.groupId() != null ? getRef(productionGroupRepository, request.groupId(), "ProductionGroup") : null);
        ticket.setPoNumber(request.poNumber());
        ticket.setStyle(request.style());
        ticket.setCustomerName(request.customerName());
        ticket.setGarmentType(getRef(garmentTypeRepository, request.garmentTypeId(), "GarmentType"));
        ticket.setInspectionStage(request.inspectionStage());
        ticket.setInspectedQty(request.inspectedQty());
        ticket.setStatus(request.status());
        ticket.setSpecNote(request.specNote());
        ticket.setQualityNote(request.qualityNote());
    }

    private void rebuildDefects(QaTicket ticket, QaTicketRequest request) {
        ticket.getDefects().clear();
        if (request.defects() == null) {
            return;
        }
        for (QaTicketDefectRequest defectReq : request.defects()) {
            QaTicketDefect defect = new QaTicketDefect();
            DefectItem defectItem = getRef(defectItemRepository, defectReq.defectItemId(), "DefectItem");
            defect.setDefectItem(defectItem);
            defect.setSeverity(resolveSeverity(defectItem, defectReq.severity()));
            defect.setNote(defectReq.note());
            ticket.addDefect(defect);

            if (defectReq.locations() != null) {
                for (QaTicketDefectLocationRequest locReq : defectReq.locations()) {
                    QaTicketDefectLocation location = new QaTicketDefectLocation();
                    location.setGarmentLocation(locReq.garmentLocationId() != null
                            ? getRef(garmentLocationRepository, locReq.garmentLocationId(), "GarmentLocation")
                            : null);
                    location.setLocationText(locReq.locationText());
                    location.setQuantity(locReq.quantity());
                    defect.addLocation(location);

                    if (locReq.images() != null) {
                        for (String imageUrl : locReq.images()) {
                            QaTicketDefectImage image = new QaTicketDefectImage();
                            image.setImageUrl(imageUrl);
                            location.addImage(image);
                        }
                    }
                }
            }
        }
    }

    private void rebuildSpecImages(QaTicket ticket, QaTicketRequest request) {
        ticket.getSpecImages().clear();
        if (request.specImages() == null || request.specImages().isEmpty()) {
            return;
        }
        if (request.inspectionStage() != InspectionStage.FINAL && request.inspectionStage() != InspectionStage.PREFINAL) {
            throw new IllegalArgumentException(
                    "specImages is only allowed when inspectionStage is FINAL or PREFINAL, got: "
                            + request.inspectionStage());
        }
        for (QaTicketSpecImageRequest imgReq : request.specImages()) {
            QaTicketSpecImage image = new QaTicketSpecImage();
            image.setType(imgReq.type());
            image.setImageUrl(imgReq.imageUrl());
            ticket.addSpecImage(image);
        }
    }

    private void rebuildMeasurementImages(QaTicket ticket, QaTicketRequest request) {
        ticket.getMeasurementImages().clear();
        if (request.measurementImages() == null) {
            return;
        }
        for (QaTicketMeasurementImageRequest imgReq : request.measurementImages()) {
            QaTicketMeasurementImage image = new QaTicketMeasurementImage();
            image.setImageUrl(imgReq.imageUrl());
            ticket.addMeasurementImage(image);
        }
    }

    /**
     * AQL sampling (aqlLevel/qtySize/actual defect counts) is only meaningful once inspection
     * reaches FINAL or PREFINAL stage, mirroring the existing specImages rule above.
     */
    private void applyAqlSampling(QaTicket ticket, QaTicketRequest request) {
        boolean anyAqlFieldSet = request.aqlLevel() != null || request.qtySize() != null
                || request.actualMajorDefects() != null || request.actualMinorDefects() != null;
        if (!anyAqlFieldSet) {
            ticket.setAqlLevel(null);
            ticket.setQtySize(null);
            ticket.setSamplingSize(null);
            ticket.setActualMajorDefects(null);
            ticket.setActualMinorDefects(null);
            ticket.setInspectionResult(null);
            return;
        }
        if (request.inspectionStage() != InspectionStage.FINAL && request.inspectionStage() != InspectionStage.PREFINAL) {
            throw new IllegalArgumentException(
                    "aqlLevel/qtySize/actualMajorDefects/actualMinorDefects are only allowed when inspectionStage is FINAL or PREFINAL, got: "
                            + request.inspectionStage());
        }
        if (request.aqlLevel() == null || request.qtySize() == null) {
            throw new IllegalArgumentException("aqlLevel and qtySize must be provided together");
        }

        AqlSamplingPlan plan = aqlSamplingService.lookup(request.aqlLevel(), request.qtySize());
        ticket.setAqlLevel(request.aqlLevel());
        ticket.setQtySize(request.qtySize());
        ticket.setSamplingSize(plan.getSamplingSize());
        ticket.setActualMajorDefects(request.actualMajorDefects());
        ticket.setActualMinorDefects(request.actualMinorDefects());

        if (request.actualMajorDefects() != null && request.actualMinorDefects() != null) {
            InspectionResult result = aqlSamplingService.evaluateResult(
                    plan, request.actualMajorDefects(), request.actualMinorDefects());
            ticket.setInspectionResult(result);
        } else {
            ticket.setInspectionResult(null);
        }
    }

    private Severity resolveSeverity(DefectItem item, Severity requested) {
        boolean allowMinor = item.isAllowMinor();
        boolean allowMajor = item.isAllowMajor();
        if (allowMinor != allowMajor) {
            return allowMinor ? Severity.MINOR : Severity.MAJOR;
        }
        if (requested == null) {
            throw new IllegalArgumentException(
                    "Severity (minor or major) must be selected for defect item: " + item.getId());
        }
        return requested;
    }

    private <T, ID> T getRef(org.springframework.data.repository.CrudRepository<T, ID> repository, ID id, String entityName) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(entityName + " not found: " + id));
    }
}
