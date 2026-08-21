package com.qms.qms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "qa_ticket_defect_location")
@Getter
@Setter
@NoArgsConstructor
public class QaTicketDefectLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qa_ticket_defect_id", nullable = false)
    private QaTicketDefect qaTicketDefect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garment_location_id")
    private GarmentLocation garmentLocation;

    @Column(name = "location_text", nullable = false, length = 150)
    private String locationText;

    @Column(nullable = false)
    private Integer quantity = 1;

    @OneToMany(mappedBy = "qaTicketDefectLocation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private Set<QaTicketDefectImage> images = new LinkedHashSet<>();

    public void addImage(QaTicketDefectImage image) {
        images.add(image);
        image.setQaTicketDefectLocation(this);
    }
}
