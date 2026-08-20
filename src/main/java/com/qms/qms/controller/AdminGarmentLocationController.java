package com.qms.qms.controller;

import com.qms.qms.dto.admin.CreateGarmentLocationRequest;
import com.qms.qms.dto.admin.UpdateGarmentLocationRequest;
import com.qms.qms.dto.master.GarmentLocationResponse;
import com.qms.qms.entity.GarmentLocation;
import com.qms.qms.entity.GarmentType;
import com.qms.qms.entity.enums.StaffRole;
import com.qms.qms.exception.ResourceNotFoundException;
import com.qms.qms.repository.GarmentLocationRepository;
import com.qms.qms.repository.GarmentTypeRepository;
import com.qms.qms.security.StaffPrincipal;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/garment-locations")
public class AdminGarmentLocationController {

    private final GarmentLocationRepository garmentLocationRepository;
    private final GarmentTypeRepository garmentTypeRepository;

    public AdminGarmentLocationController(GarmentLocationRepository garmentLocationRepository,
                                           GarmentTypeRepository garmentTypeRepository) {
        this.garmentLocationRepository = garmentLocationRepository;
        this.garmentTypeRepository = garmentTypeRepository;
    }

    @CacheEvict(cacheNames = "garmentLocations", allEntries = true)
    @PostMapping
    public ResponseEntity<GarmentLocationResponse> create(@Valid @RequestBody CreateGarmentLocationRequest request,
                                                            @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        GarmentType garmentType = garmentTypeRepository.findById(request.garmentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Garment type not found: " + request.garmentTypeId()));

        GarmentLocation location = new GarmentLocation();
        location.setGarmentType(garmentType);
        location.setName(request.name());
        location = garmentLocationRepository.save(location);
        return ResponseEntity.status(HttpStatus.CREATED).body(GarmentLocationResponse.from(location));
    }

    @CacheEvict(cacheNames = "garmentLocations", allEntries = true)
    @PutMapping("/{id}")
    public ResponseEntity<GarmentLocationResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateGarmentLocationRequest request,
                                                            @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        GarmentLocation location = garmentLocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Garment location not found: " + id));
        GarmentType garmentType = garmentTypeRepository.findById(request.garmentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Garment type not found: " + request.garmentTypeId()));

        location.setGarmentType(garmentType);
        location.setName(request.name());
        location = garmentLocationRepository.save(location);
        return ResponseEntity.ok(GarmentLocationResponse.from(location));
    }

    @CacheEvict(cacheNames = "garmentLocations", allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        if (!garmentLocationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Garment location not found: " + id);
        }
        garmentLocationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(StaffPrincipal principal) {
        if (principal.getStaff().getRole() != StaffRole.ADMIN) {
            throw new AccessDeniedException("Only admins can manage garment locations");
        }
    }
}
