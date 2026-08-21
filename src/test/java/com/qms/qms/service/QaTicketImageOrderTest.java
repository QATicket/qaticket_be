package com.qms.qms.service;

import com.qms.qms.dto.ticket.*;
import com.qms.qms.entity.*;
import com.qms.qms.entity.enums.*;
import com.qms.qms.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test cho bug "ảnh lưu vào DB không đúng thứ tự upload".
 *
 * Root cause: các quan hệ ảnh/defect/location được map bằng {@code Set} không
 * có {@code @OrderBy}, nên Hibernate không đảm bảo thứ tự khi load lại từ DB
 * dù id vẫn tăng đúng thứ tự tạo. Test này tạo ticket với ảnh có thứ tự biết
 * trước, ép Hibernate bỏ persistence-context cache rồi load lại (giả lập 1
 * request GET mới), và so khớp thứ tự trả về với thứ tự đã gửi lên.
 *
 * Chạy trên H2 in-memory (profile "test", xem application-test.properties) -
 * schema tự tạo/xoá theo từng lần chạy test, không đụng vào DB thật, và
 * @Transactional ở đây tự rollback sau khi test xong nên không để lại data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QaTicketImageOrderTest {

    @Autowired
    private QaTicketService qaTicketService;
    @Autowired
    private QaTicketRepository qaTicketRepository;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private FactoryRepository factoryRepository;
    @Autowired
    private LineRepository lineRepository;
    @Autowired
    private GarmentTypeRepository garmentTypeRepository;
    @Autowired
    private DefectRepository defectRepository;
    @Autowired
    private DefectItemRepository defectItemRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void imagesAndNestedCollectionsKeepUploadOrderAfterFreshReload() {
        // V1__init.sql tao sequence nay bang Flyway (dang tat o profile "test", xem
        // application-test.properties) - H2 ddl-auto=create-drop chi sinh schema tu entity
        // nen khong biet sequence nay, phai tao tay truoc khi goi qaTicketService.create().
        entityManager.createNativeQuery("CREATE SEQUENCE IF NOT EXISTS qa_ticket_seq").executeUpdate();

        Staff staff = new Staff();
        staff.setCode("T-QA");
        staff.setFullName("Test QA");
        staff.setPassword("x");
        staff.setRole(StaffRole.QA_INSPECTOR);
        staff.setActive(true);
        staff.setLanguage(StaffLanguage.VI);
        staff = staffRepository.save(staff);

        Factory factory = new Factory();
        factory.setCode("T-FCT");
        factory.setName("Test Factory");
        factory = factoryRepository.save(factory);

        Line line = new Line();
        line.setFactory(factory);
        line.setCode("T-L1");
        line.setName("Test Line");
        line = lineRepository.save(line);

        GarmentType garmentType = new GarmentType();
        garmentType.setName("Test Garment " + System.nanoTime());
        garmentType = garmentTypeRepository.save(garmentType);

        Defect defect = new Defect();
        defect.setCode("T-DF");
        defect.setNameVi("Loi test");
        defect = defectRepository.save(defect);

        DefectItem defectItem1 = newDefectItem(defect, "T-DI-1", "Loi test 1");
        DefectItem defectItem2 = newDefectItem(defect, "T-DI-2", "Loi test 2");

        // Thu tu "upload" gia lap - giu nguyen thu tu nay xuyen suot request/assertion ben duoi.
        List<String> location1Images = List.of(
                "loc1-img-01.jpg", "loc1-img-02.jpg", "loc1-img-03.jpg", "loc1-img-04.jpg",
                "loc1-img-05.jpg", "loc1-img-06.jpg", "loc1-img-07.jpg", "loc1-img-08.jpg");
        List<String> location2Images = List.of(
                "loc2-img-01.jpg", "loc2-img-02.jpg", "loc2-img-03.jpg");
        List<String> measurementImages = List.of(
                "measure-01.jpg", "measure-02.jpg", "measure-03.jpg", "measure-04.jpg",
                "measure-05.jpg", "measure-06.jpg", "measure-07.jpg");
        List<String> specImages = List.of(
                "spec-01.jpg", "spec-02.jpg", "spec-03.jpg", "spec-04.jpg", "spec-05.jpg");

        QaTicketDefectLocationRequest loc1 =
                new QaTicketDefectLocationRequest(null, "Vi tri 1", 1, location1Images);
        QaTicketDefectLocationRequest loc2 =
                new QaTicketDefectLocationRequest(null, "Vi tri 2", 1, location2Images);

        QaTicketDefectRequest defectReq1 =
                new QaTicketDefectRequest(defectItem1.getId(), Severity.MAJOR, "note 1", List.of(loc1, loc2));
        QaTicketDefectRequest defectReq2 =
                new QaTicketDefectRequest(defectItem2.getId(), Severity.MINOR, "note 2", List.of());

        List<QaTicketMeasurementImageRequest> measurementReq =
                measurementImages.stream().map(QaTicketMeasurementImageRequest::new).toList();
        List<QaTicketSpecImageRequest> specReq =
                specImages.stream().map(url -> new QaTicketSpecImageRequest(SpecImageType.PACKING, url)).toList();

        QaTicketRequest request = new QaTicketRequest(
                staff.getId(), factory.getId(), line.getId(), null,
                InspectionStage.FINAL, "PO-TEST", "STYLE-TEST", 10, "Test Customer",
                garmentType.getId(), TicketStatus.DRAFT,
                null, null, null, null,
                "spec note", "quality note",
                List.of(defectReq1, defectReq2), specReq, measurementReq);

        Long ticketId = qaTicketService.create(request).id();

        // Bo persistence-context cache -> lan load ke tiep chac chan la 1 lan doc moi tu DB,
        // dung nhu khi mot request GET /api/qa-tickets/{id} khac den sau.
        entityManager.flush();
        entityManager.clear();

        QaTicket reloaded = qaTicketRepository.findWithDetailsById(ticketId).orElseThrow();
        QaTicketResponse response = QaTicketResponse.from(reloaded);

        assertThat(response.measurementImages().stream().map(QaTicketMeasurementImageResponse::imageUrl).toList())
                .as("measurementImages phai giu dung thu tu upload")
                .isEqualTo(measurementImages);

        assertThat(response.specImages().stream().map(QaTicketSpecImageResponse::imageUrl).toList())
                .as("specImages phai giu dung thu tu upload")
                .isEqualTo(specImages);

        assertThat(response.defects()).hasSize(2);
        QaTicketDefectResponse defectResp1 = response.defects().stream()
                .filter(d -> d.defectItem().id().equals(defectItem1.getId())).findFirst().orElseThrow();
        assertThat(defectResp1.locations()).hasSize(2);

        assertThat(defectResp1.locations().get(0).images().stream()
                        .map(QaTicketDefectImageResponse::imageUrl).toList())
                .as("anh vi tri 1 phai giu dung thu tu upload")
                .isEqualTo(location1Images);
        assertThat(defectResp1.locations().get(1).images().stream()
                        .map(QaTicketDefectImageResponse::imageUrl).toList())
                .as("anh vi tri 2 phai giu dung thu tu upload")
                .isEqualTo(location2Images);
    }

    private DefectItem newDefectItem(Defect defect, String code, String nameVi) {
        DefectItem item = new DefectItem();
        item.setDefect(defect);
        item.setCode(code);
        item.setNameVi(nameVi);
        item.setAllowMinor(true);
        item.setAllowMajor(true);
        return defectItemRepository.save(item);
    }
}
