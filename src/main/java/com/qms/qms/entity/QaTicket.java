package com.qms.qms.entity;

import com.qms.qms.entity.enums.AqlLevel;
import com.qms.qms.entity.enums.AqlLevelConverter;
import com.qms.qms.entity.enums.InspectionResult;
import com.qms.qms.entity.enums.InspectionStage;
import com.qms.qms.entity.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "qa_ticket")
@Getter
@Setter
@NoArgsConstructor
public class QaTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_code", unique = true, nullable = false, length = 20)
    private String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", nullable = false)
    private Line line;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ProductionGroup group;

    @Column(name = "po_number", length = 50)
    private String poNumber;

    @Column(length = 150)
    private String style;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garment_type_id", nullable = false)
    private GarmentType garmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_stage", nullable = false, length = 30)
    private InspectionStage inspectionStage;

    @Column(name = "inspected_qty", nullable = false)
    private Integer inspectedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.DRAFT;

    @Column(nullable = false)
    private boolean exported = false;

    @Column(name = "exported_at")
    private LocalDateTime exportedAt;

    // AQL sampling plan fields, only populated when inspectionStage is FINAL or PREFINAL.
    @Convert(converter = AqlLevelConverter.class)
    @Column(name = "aql_level", length = 10)
    private AqlLevel aqlLevel;

    @Column(name = "qty_size")
    private Integer qtySize;

    @Column(name = "sampling_size")
    private Integer samplingSize;

    @Column(name = "actual_major_defects")
    private Integer actualMajorDefects;

    @Column(name = "actual_minor_defects")
    private Integer actualMinorDefects;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_result", length = 20)
    private InspectionResult inspectionResult;

    @Column(name = "spec_note", columnDefinition = "TEXT")
    private String specNote;

    @Column(name = "quality_note", columnDefinition = "TEXT")
    private String qualityNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "qaTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private Set<QaTicketDefect> defects = new LinkedHashSet<>();

    @OneToMany(mappedBy = "qaTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private Set<QaTicketSpecImage> specImages = new LinkedHashSet<>();

    @OneToMany(mappedBy = "qaTicket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private Set<QaTicketMeasurementImage> measurementImages = new LinkedHashSet<>();

    public void addDefect(QaTicketDefect defect) {
        defects.add(defect);
        defect.setQaTicket(this);
    }

    public void addSpecImage(QaTicketSpecImage image) {
        specImages.add(image);
        image.setQaTicket(this);
    }

    public void addMeasurementImage(QaTicketMeasurementImage image) {
        measurementImages.add(image);
        image.setQaTicket(this);
    }
}
