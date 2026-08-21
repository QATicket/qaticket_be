package com.qms.qms.controller;

import com.qms.qms.dto.admin.CreateFactoryRequest;
import com.qms.qms.dto.admin.UpdateFactoryRequest;
import com.qms.qms.dto.master.FactoryResponse;
import com.qms.qms.entity.Staff;
import com.qms.qms.entity.enums.StaffLanguage;
import com.qms.qms.entity.enums.StaffRole;
import com.qms.qms.repository.StaffRepository;
import com.qms.qms.security.StaffPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * CRUD Factory (Admin) phải evict cache "factories" (dùng bởi GET /api/master/factories,
 * @Cacheable("factories") - xem MasterDataController) sau mỗi create/update/delete, nếu
 * không dropdown chọn nhà máy ở FE sẽ hiện dữ liệu cũ cho tới khi cache tự hết hạn (24h,
 * xem CacheConfig). Test gọi thẳng qua bean Spring (không phải instance new trực tiếp) để
 * đi qua đúng AOP proxy của @Cacheable/@CacheEvict.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminFactoryControllerTest {

    @Autowired
    private AdminFactoryController adminFactoryController;
    @Autowired
    private MasterDataController masterDataController;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void createUpdateDeleteEvictFactoriesCache() {
        Staff admin = newStaff("T-ADM", StaffRole.ADMIN);
        Staff nonAdmin = newStaff("T-QA", StaffRole.QA_INSPECTOR);
        StaffPrincipal adminPrincipal = new StaffPrincipal(admin);
        StaffPrincipal nonAdminPrincipal = new StaffPrincipal(nonAdmin);

        // Mồi cache bằng 1 lần gọi qua bean thật (@Cacheable chỉ kích hoạt qua proxy).
        int before = masterDataController.factories().size();

        var created = adminFactoryController.create(
                new CreateFactoryRequest("T-FCT-CACHE", "Cache Test Factory", "123 Test St"),
                adminPrincipal).getBody();
        assertThat(created.name()).isEqualTo("Cache Test Factory");

        List<FactoryResponse> afterCreate = masterDataController.factories();
        assertThat(afterCreate).as("cache phai duoc evict ngay sau create, khong tra danh sach cu")
                .hasSize(before + 1)
                .anyMatch(f -> f.id().equals(created.id()) && f.name().equals("Cache Test Factory"));

        adminFactoryController.update(created.id(),
                new UpdateFactoryRequest("T-FCT-CACHE", "Cache Test Factory Updated", "456 Test St"),
                adminPrincipal);

        List<FactoryResponse> afterUpdate = masterDataController.factories();
        assertThat(afterUpdate).as("cache phai duoc evict ngay sau update")
                .hasSize(before + 1)
                .anyMatch(f -> f.id().equals(created.id()) && f.name().equals("Cache Test Factory Updated"));

        // Trung ma code voi factory vua tao phai bi tu choi (409/IllegalArgumentException).
        assertThatThrownBy(() -> adminFactoryController.create(
                new CreateFactoryRequest("T-FCT-CACHE", "Duplicate Code Factory", null), adminPrincipal))
                .isInstanceOf(IllegalArgumentException.class);

        // Non-admin khong duoc tao/sua/xoa factory.
        Throwable deniedEx = catchThrowable(() -> adminFactoryController.create(
                new CreateFactoryRequest("T-FCT-DENY", "Should Be Denied", null), nonAdminPrincipal));
        assertThat(deniedEx).isInstanceOf(AccessDeniedException.class);

        adminFactoryController.delete(created.id(), adminPrincipal);

        List<FactoryResponse> afterDelete = masterDataController.factories();
        assertThat(afterDelete).as("cache phai duoc evict ngay sau delete")
                .hasSize(before)
                .noneMatch(f -> f.id().equals(created.id()));
    }

    private Staff newStaff(String code, StaffRole role) {
        Staff staff = new Staff();
        staff.setCode(code + "-" + System.nanoTime());
        staff.setFullName("Test " + code);
        staff.setPassword("x");
        staff.setRole(role);
        staff.setActive(true);
        staff.setLanguage(StaffLanguage.VI);
        staffRepository.save(staff);
        entityManager.flush();
        return staff;
    }
}
