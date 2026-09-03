package com.homerun.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.document.dto.HoldingDtos.HoldingList;
import com.homerun.domain.document.dto.HoldingDtos.HoldingView;
import com.homerun.domain.document.dto.HoldingDtos.RecordRequest;
import com.homerun.domain.document.repository.DocumentTypeRepository;
import com.homerun.domain.document.repository.UserDocumentRepository;
import com.homerun.domain.document.service.DocumentHoldingService;
import com.homerun.domain.document.type.DocumentHoldingStatus;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서류 보유 상태와 유효기간(EVI-01-04 · EVI-01-07).
 *
 * <p>등기부·주민등록등본·가족관계증명서는 1개월 이내 발급분만 인정된다(FCT-112). 나머지는
 * 근거가 없어 인정 기간을 비워 뒀으므로 만료를 따지지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DocumentHoldingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private final UserDocumentRepository holdings;
    private final DocumentTypeRepository documents;
    private final PlanRepository plans;
    private final EntityManager em;
    private final DocumentHoldingService service;

    private Long ownerId;
    private Long planId;

    DocumentHoldingServiceTest(
            @Autowired UserDocumentRepository holdings,
            @Autowired DocumentTypeRepository documents,
            @Autowired PlanRepository plans,
            @Autowired EntityManager em) {
        this.holdings = holdings;
        this.documents = documents;
        this.plans = plans;
        this.em = em;
        this.service = serviceAt(TODAY);
    }

    private DocumentHoldingService serviceAt(LocalDate date) {
        return new DocumentHoldingService(
                holdings,
                documents,
                plans,
                Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    @BeforeEach
    void setUpPlan() {
        ownerId = newMember();
        planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", ownerId)
                        .getSingleResult();
        em.flush();
        em.clear();
    }

    private Long newMember() {
        return (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
    }

    private static HoldingView find(HoldingList list, String code) {
        return list.documents().stream()
                .filter(view -> view.documentCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("서류가 없다: " + code));
    }

    private HoldingList record(String code, DocumentHoldingStatus status, LocalDate issuedAt) {
        return service.record(ownerId, planId, new RecordRequest(code, status, issuedAt));
    }

    // EVI-01-04 서류 준비 상태

    @Test
    @DisplayName("서류 상태를 기록하고 다시 읽는다")
    void should_record_and_read_status() {
        record("RESIDENT_LIST", DocumentHoldingStatus.IN_PROGRESS, null);

        HoldingView view = find(service.list(ownerId, planId), "RESIDENT_LIST");

        assertThat(view.status()).isEqualTo(DocumentHoldingStatus.IN_PROGRESS);
        assertThat(view.statusLabel()).isEqualTo("준비 중");
        assertThat(view.action()).isNotNull();
    }

    @Test
    @DisplayName("같은 서류를 다시 기록하면 덮어쓴다")
    void should_overwrite_same_document() {
        record("RESIDENT_LIST", DocumentHoldingStatus.NEEDED, null);
        record("RESIDENT_LIST", DocumentHoldingStatus.ISSUED, TODAY);

        assertThat(service.list(ownerId, planId).documents()).hasSize(1);
        assertThat(find(service.list(ownerId, planId), "RESIDENT_LIST").status())
                .isEqualTo(DocumentHoldingStatus.ISSUED);
    }

    @Test
    @DisplayName("재발급 필요는 직접 지정할 수 없다")
    void should_reject_manually_setting_expired() {
        assertThatThrownBy(() -> record("RESIDENT_LIST", DocumentHoldingStatus.EXPIRED, TODAY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DOCUMENT_STATUS_NOT_SELECTABLE);
    }

    @Test
    @DisplayName("없는 서류 코드는 404 다")
    void should_reject_unknown_document() {
        assertThatThrownBy(() -> record("NO_SUCH", DocumentHoldingStatus.NEEDED, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 계획은 볼 수 없다")
    void should_reject_other_members_plan() {
        Long stranger = newMember();

        assertThatThrownBy(() -> service.list(stranger, planId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PLAN_ACCESS_DENIED);
    }

    // EVI-01-07 서류 유효성

    @Test
    @DisplayName("인정 기간이 있는 서류는 발급일로 만료일이 정해진다")
    void should_compute_expiry_from_issue_date() {
        HoldingView view =
                find(record("RESIDENT_REGISTRATION", DocumentHoldingStatus.ISSUED, TODAY), "RESIDENT_REGISTRATION");

        assertThat(view.expiresAt()).isEqualTo(TODAY.plusDays(30));
        assertThat(view.daysUntilExpiry()).isEqualTo(30);
    }

    @Test
    @DisplayName("인정 기간이 지나면 재발급 필요로 바뀐다")
    void should_mark_expired_when_validity_passed() {
        // 은행 상담 D-21 에 맞춰 미리 뗀 등본이 잔금일에는 만료된 경우다.
        record("RESIDENT_REGISTRATION", DocumentHoldingStatus.ISSUED, TODAY.minusDays(31));

        HoldingList list = service.list(ownerId, planId);

        assertThat(find(list, "RESIDENT_REGISTRATION").status()).isEqualTo(DocumentHoldingStatus.EXPIRED);
        assertThat(list.needsReissue()).containsExactly("RESIDENT_REGISTRATION");
        assertThat(find(list, "RESIDENT_REGISTRATION").action()).contains("다시 발급");
    }

    @Test
    @DisplayName("만료가 가까우면 미리 알린다")
    void should_warn_before_expiry() {
        record("RESIDENT_REGISTRATION", DocumentHoldingStatus.ISSUED, TODAY.minusDays(25));

        HoldingList list = service.list(ownerId, planId);

        assertThat(find(list, "RESIDENT_REGISTRATION").expiringSoon()).isTrue();
        assertThat(list.expiringSoon()).containsExactly("RESIDENT_REGISTRATION");
        assertThat(list.needsReissue()).isEmpty();
    }

    @Test
    @DisplayName("인정 기간을 모르는 서류는 만료로 보지 않는다")
    void should_not_expire_document_without_known_validity() {
        // 소득금액증명은 제출처마다 인정 기간이 달라 비워 뒀다(#59 리뷰).
        record("INCOME_CERT", DocumentHoldingStatus.ISSUED, TODAY.minusYears(2));

        HoldingView view = find(service.list(ownerId, planId), "INCOME_CERT");

        assertThat(view.status()).isEqualTo(DocumentHoldingStatus.ISSUED);
        assertThat(view.expiresAt()).isNull();
        assertThat(view.daysUntilExpiry()).isNull();
    }

    @Test
    @DisplayName("인정 기간을 모르면 모른다고 말한다")
    void should_say_validity_is_unknown() {
        record("INCOME_CERT", DocumentHoldingStatus.ISSUED, TODAY);

        assertThat(find(service.list(ownerId, planId), "INCOME_CERT").validityNote())
                .isNotBlank();
    }

    @Test
    @DisplayName("발급일을 모르면 만료일을 만들지 않는다")
    void should_not_compute_expiry_without_issue_date() {
        HoldingView view =
                find(record("RESIDENT_REGISTRATION", DocumentHoldingStatus.IN_PROGRESS, null), "RESIDENT_REGISTRATION");

        assertThat(view.expiresAt()).isNull();
    }

    @Test
    @DisplayName("손에 없는 서류는 만료를 따지지 않는다")
    void should_not_expire_document_not_in_hand() {
        record("RESIDENT_REGISTRATION", DocumentHoldingStatus.ISSUED, TODAY.minusDays(31));
        record("RESIDENT_REGISTRATION", DocumentHoldingStatus.NEEDED, null);

        assertThat(find(service.list(ownerId, planId), "RESIDENT_REGISTRATION").status())
                .isEqualTo(DocumentHoldingStatus.NEEDED);
    }

    @Test
    @DisplayName("발급일을 지우면 만료일도 함께 지운다")
    void should_clear_expiry_when_issue_date_is_removed() {
        record("RESIDENT_REGISTRATION", DocumentHoldingStatus.ISSUED, TODAY);

        HoldingView view =
                find(record("RESIDENT_REGISTRATION", DocumentHoldingStatus.NEEDED, null), "RESIDENT_REGISTRATION");

        assertThat(view.expiresAt()).isNull();
    }
}
