package com.qms.qms.controller;

import com.qms.qms.dto.admin.CreateDefectItemRequest;
import com.qms.qms.dto.admin.UpdateDefectItemRequest;
import com.qms.qms.dto.master.DefectItemResponse;
import com.qms.qms.entity.Defect;
import com.qms.qms.entity.DefectItem;
import com.qms.qms.entity.enums.StaffRole;
import com.qms.qms.exception.ResourceNotFoundException;
import com.qms.qms.repository.DefectItemRepository;
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
@RequestMapping("/api/admin/defect-items")
public class AdminDefectItemController {

    private final DefectItemRepository defectItemRepository;
    private final DefectRepository defectRepository;

    public AdminDefectItemController(DefectItemRepository defectItemRepository, DefectRepository defectRepository) {
        this.defectItemRepository = defectItemRepository;
        this.defectRepository = defectRepository;
    }

    @CacheEvict(cacheNames = "defectItems", allEntries = true)
    @PostMapping
    public ResponseEntity<DefectItemResponse> create(@Valid @RequestBody CreateDefectItemRequest request,
                                                       @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        requireCodeAvailable(request.code(), null);
        boolean allowMinor = request.allowMinor() == null || request.allowMinor();
        boolean allowMajor = request.allowMajor() == null || request.allowMajor();
        requireAtLeastOneSeverity(allowMinor, allowMajor);
        Defect defect = defectRepository.findById(request.defectId())
                .orElseThrow(() -> new ResourceNotFoundException("Defect not found: " + request.defectId()));

        DefectItem item = new DefectItem();
        item.setDefect(defect);
        item.setCode(request.code());
        item.setNameEn(request.nameEn());
        item.setNameVi(request.nameVi());
        item.setAllowMinor(allowMinor);
        item.setAllowMajor(allowMajor);
        item = defectItemRepository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(DefectItemResponse.from(item));
    }

    @CacheEvict(cacheNames = "defectItems", allEntries = true)
    @PutMapping("/{id}")
    public ResponseEntity<DefectItemResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateDefectItemRequest request,
                                                       @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        DefectItem item = defectItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Defect item not found: " + id));
        requireCodeAvailable(request.code(), id);
        boolean allowMinor = request.allowMinor() == null || request.allowMinor();
        boolean allowMajor = request.allowMajor() == null || request.allowMajor();
        requireAtLeastOneSeverity(allowMinor, allowMajor);
        Defect defect = defectRepository.findById(request.defectId())
                .orElseThrow(() -> new ResourceNotFoundException("Defect not found: " + request.defectId()));

        item.setDefect(defect);
        item.setCode(request.code());
        item.setNameEn(request.nameEn());
        item.setNameVi(request.nameVi());
        item.setAllowMinor(allowMinor);
        item.setAllowMajor(allowMajor);
        item = defectItemRepository.save(item);
        return ResponseEntity.ok(DefectItemResponse.from(item));
    }

    @CacheEvict(cacheNames = "defectItems", allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        if (!defectItemRepository.existsById(id)) {
            throw new ResourceNotFoundException("Defect item not found: " + id);
        }
        defectItemRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAtLeastOneSeverity(boolean allowMinor, boolean allowMajor) {
        if (!allowMinor && !allowMajor) {
            throw new IllegalArgumentException("Phải cho phép ít nhất một mức độ lỗi (minor hoặc major)");
        }
    }

    private void requireCodeAvailable(String code, Long excludeId) {
        if (code == null) {
            return;
        }
        defectItemRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Mã defect item đã tồn tại: " + code);
                });
    }

    private void requireAdmin(StaffPrincipal principal) {
        if (principal.getStaff().getRole() != StaffRole.ADMIN) {
            throw new AccessDeniedException("Only admins can manage defect items");
        }
    }
}
