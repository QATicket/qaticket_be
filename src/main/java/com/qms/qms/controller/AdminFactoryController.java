package com.qms.qms.controller;

import com.qms.qms.dto.admin.CreateFactoryRequest;
import com.qms.qms.dto.admin.UpdateFactoryRequest;
import com.qms.qms.dto.master.FactoryResponse;
import com.qms.qms.entity.Factory;
import com.qms.qms.entity.enums.StaffRole;
import com.qms.qms.exception.ResourceNotFoundException;
import com.qms.qms.repository.FactoryRepository;
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
@RequestMapping("/api/admin/factories")
public class AdminFactoryController {

    private final FactoryRepository factoryRepository;

    public AdminFactoryController(FactoryRepository factoryRepository) {
        this.factoryRepository = factoryRepository;
    }

    @CacheEvict(cacheNames = "factories", allEntries = true)
    @PostMapping
    public ResponseEntity<FactoryResponse> create(@Valid @RequestBody CreateFactoryRequest request,
                                                    @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        requireCodeAvailable(request.code(), null);
        Factory factory = new Factory();
        factory.setCode(request.code());
        factory.setName(request.name());
        factory.setAddress(request.address());
        factory = factoryRepository.save(factory);
        return ResponseEntity.status(HttpStatus.CREATED).body(FactoryResponse.from(factory));
    }

    @CacheEvict(cacheNames = "factories", allEntries = true)
    @PutMapping("/{id}")
    public ResponseEntity<FactoryResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateFactoryRequest request,
                                                    @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        Factory factory = factoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factory not found: " + id));
        requireCodeAvailable(request.code(), id);
        factory.setCode(request.code());
        factory.setName(request.name());
        factory.setAddress(request.address());
        factory = factoryRepository.save(factory);
        return ResponseEntity.ok(FactoryResponse.from(factory));
    }

    // Factory không cascade xoá Line (chỉ Line giữ FK factory_id) - nếu factory đang có
    // Line/QaTicket tham chiếu, DB chặn bằng foreign key constraint và ném
    // DataIntegrityViolationException, đã được GlobalExceptionHandler bắt trả về 409.
    @CacheEvict(cacheNames = "factories", allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal principal) {
        requireAdmin(principal);
        if (!factoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Factory not found: " + id);
        }
        factoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void requireCodeAvailable(String code, Long excludeId) {
        if (code == null) {
            return;
        }
        factoryRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Mã nhà máy đã tồn tại: " + code);
                });
    }

    private void requireAdmin(StaffPrincipal principal) {
        if (principal.getStaff().getRole() != StaffRole.ADMIN) {
            throw new AccessDeniedException("Only admins can manage factories");
        }
    }
}
