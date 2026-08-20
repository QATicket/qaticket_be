package com.qms.qms.controller;

import com.qms.qms.dto.admin.CreateDefectRequest;
import com.qms.qms.dto.admin.UpdateDefectRequest;
import com.qms.qms.dto.master.DefectResponse;
import com.qms.qms.entity.Defect;
import com.qms.qms.entity.enums.StaffRole;
import com.qms.qms.exception.ResourceNotFoundException;
import com.qms.qms.repository.DefectRepository;
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
@RequestMapping("/api/admin/defects")
public class AdminDefectController {

    private final DefectRepository defectRepository;

    public AdminDefectController(DefectRepository defectRepository) {
        this.defectRepository = defectRepository;
    }

    @CacheEvict(cacheNames = "defects", allEntries = true)
    @PostMapping
    public ResponseEntity<DefectResponse> create(@Valid @RequestBody CreateDefectRequest request,
                                                  @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        requireCodeAvailable(request.code(), null);
        Defect defect = new Defect();
        defect.setCode(request.code());
        defect.setNameEn(request.nameEn());
        defect.setNameVi(request.nameVi());
        defect = defectRepository.save(defect);
        return ResponseEntity.status(HttpStatus.CREATED).body(DefectResponse.from(defect));
    }

    @CacheEvict(cacheNames = "defects", allEntries = true)
    @PutMapping("/{id}")
    public ResponseEntity<DefectResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateDefectRequest request,
                                                  @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        Defect defect = defectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Defect not found: " + id));
        requireCodeAvailable(request.code(), id);
        defect.setCode(request.code());
        defect.setNameEn(request.nameEn());
        defect.setNameVi(request.nameVi());
        defect = defectRepository.save(defect);
        return ResponseEntity.ok(DefectResponse.from(defect));
    }

    @CacheEvict(cacheNames = {"defects", "defectItems"}, allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        if (!defectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Defect not found: " + id);
        }
        defectRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void requireCodeAvailable(String code, Long excludeId) {
        if (code == null) {
            return;
        }
        defectRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Mã lỗi đã tồn tại: " + code);
                });
    }

    private void requireAdmin(StaffPrincipal principal) {
        if (principal.getStaff().getRole() != StaffRole.ADMIN) {
            throw new AccessDeniedException("Only admins can manage defects");
        }
    }
}
