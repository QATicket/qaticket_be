package com.qms.qms.entity;

import com.qms.qms.entity.enums.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "qa_ticket_defect")
@Getter
@Setter
@NoArgsConstructor
public class QaTicketDefect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qa_ticket_id", nullable = false)
    private QaTicket qaTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_item_id", nullable = false)
    private DefectItem defectItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(length = 255)
    private String note;

    @OneToMany(mappedBy = "qaTicketDefect", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private Set<QaTicketDefectLocation> locations = new LinkedHashSet<>();

    public void addLocation(QaTicketDefectLocation location) {
        locations.add(location);
        location.setQaTicketDefect(this);
    }
}
