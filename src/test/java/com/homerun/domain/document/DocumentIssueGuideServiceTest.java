package com.homerun.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.document.dto.DocumentDtos.DocumentIssueGuide;
import com.homerun.domain.document.dto.DocumentDtos.DocumentSummary;
import com.homerun.domain.document.dto.DocumentDtos.IssueMethodView;
import com.homerun.domain.document.service.DocumentIssueGuideService;
import com.homerun.domain.document.type.IssueMethod;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DocumentIssueGuideServiceTest {

    private final DocumentIssueGuideService service;

    DocumentIssueGuideServiceTest(@Autowired DocumentIssueGuideService service) {
        this.service = service;
    }

    private static IssueMethodView method(DocumentIssueGuide guide, IssueMethod method) {
        return guide.methods().stream()
                .filter(view -> view.method() == method)
                .findFirst()
                .orElseThrow(() -> new AssertionError("발급방법이 없다: " + method));
    }

    @Test
    @DisplayName("시드된 서류가 목록으로 나온다")
    void should_list_seeded_documents() {
        assertThat(service.list().documents())
                .extracting(DocumentSummary::code)
                .contains(
                        "REGISTRY_CERT",
                        "BUILDING_LEDGER",
                        "RESIDENT_REGISTRATION",
                        "RESIDENT_LIST",
                        "LEASE_AGREEMENT");
    }

    @Test
    @DisplayName("없는 서류 코드는 404 다")
    void should_reject_unknown_code() {
        assertThatThrownBy(() -> service.guide("NO_SUCH_DOCUMENT"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    // ISS-01-01 발급방법 안내

    @Test
    @DisplayName("한 서류의 발급방법을 여러 개 준다")
    void should_offer_multiple_issue_methods() {
        assertThat(service.guide("RESIDENT_REGISTRATION").methods())
                .extracting(IssueMethodView::method)
                .containsExactlyInAnyOrder(IssueMethod.ONLINE, IssueMethod.KIOSK, IssueMethod.VISIT);
    }

    @Test
    @DisplayName("발급방법마다 화면에 쓸 이름이 붙는다")
    void should_label_each_method() {
        assertThat(service.guide("RESIDENT_REGISTRATION").methods())
                .allMatch(view -> view.label() != null && !view.label().isBlank());
    }

    // ISS-01-02 온라인 우선

    @Test
    @DisplayName("온라인으로 뗄 수 있으면 온라인이 맨 앞이고 권장이다")
    void should_recommend_online_first() {
        DocumentIssueGuide guide = service.guide("RESIDENT_REGISTRATION");

        assertThat(guide.methods().get(0).method()).isEqualTo(IssueMethod.ONLINE);
        assertThat(guide.methods().get(0).recommended()).isTrue();
        assertThat(guide.onlineOnlyBlocked()).isFalse();
    }

    @Test
    @DisplayName("권장은 서류마다 하나뿐이다")
    void should_recommend_exactly_one_method() {
        for (DocumentSummary summary : service.list().documents()) {
            assertThat(service.guide(summary.code()).methods())
                    .filteredOn(IssueMethodView::recommended)
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("같은 서류라도 방법마다 수수료가 다르다")
    void should_price_each_method_separately() {
        DocumentIssueGuide guide = service.guide("RESIDENT_REGISTRATION");

        assertThat(method(guide, IssueMethod.ONLINE).fee()).isZero();
        assertThat(method(guide, IssueMethod.VISIT).fee()).isEqualTo(400);
    }

    @Test
    @DisplayName("목록의 수수료는 가장 싼 방법 기준이다")
    void should_show_cheapest_fee_in_list() {
        DocumentSummary summary = service.list().documents().stream()
                .filter(document -> document.code().equals("RESIDENT_REGISTRATION"))
                .findFirst()
                .orElseThrow();

        assertThat(summary.cheapestFee()).isZero();
        assertThat(summary.onlineAvailable()).isTrue();
    }

    // ISS-01-03 공식 발급 경로

    @Test
    @DisplayName("온라인 발급방법에는 공식 페이지 주소가 있다")
    void should_provide_official_url_for_online() {
        assertThat(service.list().documents())
                .filteredOn(DocumentSummary::onlineAvailable)
                .allSatisfy(summary -> {
                    IssueMethodView online = method(service.guide(summary.code()), IssueMethod.ONLINE);
                    assertThat(online.url()).as(summary.code()).startsWith("http");
                });
    }

    // ISS-01-04 방문 준비물

    @Test
    @DisplayName("방문 발급방법에는 챙길 것이 적혀 있다")
    void should_list_requirements_for_visit() {
        assertThat(method(service.guide("RESIDENT_REGISTRATION"), IssueMethod.VISIT)
                        .requirements())
                .contains("신분증");
    }

    @Test
    @DisplayName("이해관계인만 뗄 수 있는 서류는 계약서를 챙기라고 알린다")
    void should_require_contract_for_resident_list() {
        assertThat(method(service.guide("RESIDENT_LIST"), IssueMethod.VISIT).requirements())
                .contains("계약서");
    }

    // ISS-01-05 방문 전 확인사항

    @Test
    @DisplayName("방문 발급방법에는 운영시간 같은 확인사항이 붙는다")
    void should_note_before_visit() {
        assertThat(method(service.guide("BUILDING_LEDGER"), IssueMethod.VISIT).note())
                .isNotBlank();
    }

    // 온라인이 안 되는 서류

    @Test
    @DisplayName("전입세대확인서는 온라인이 없고 방문이 권장이다")
    void should_flag_documents_that_cannot_be_issued_online() {
        DocumentIssueGuide guide = service.guide("RESIDENT_LIST");

        assertThat(guide.onlineOnlyBlocked()).isTrue();
        assertThat(guide.methods()).extracting(IssueMethodView::method).doesNotContain(IssueMethod.ONLINE);
        assertThat(guide.methods().get(0).recommended()).isTrue();
        assertThat(guide.methods().get(0).method()).isEqualTo(IssueMethod.VISIT);
    }

    // 시드 정합성

    @Test
    @DisplayName("모든 서류에 발급방법이 최소 하나 있다")
    void should_have_at_least_one_method_per_document() {
        for (DocumentSummary summary : service.list().documents()) {
            assertThat(service.guide(summary.code()).methods())
                    .as(summary.code())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("온라인 가능 표시가 실제 발급방법과 어긋나지 않는다")
    void should_keep_online_flag_consistent_with_methods() {
        for (DocumentSummary summary : service.list().documents()) {
            DocumentIssueGuide guide = service.guide(summary.code());
            boolean hasOnline = guide.methods().stream().anyMatch(view -> view.method() == IssueMethod.ONLINE);

            assertThat(summary.onlineAvailable()).as(summary.code()).isEqualTo(hasOnline);
            assertThat(guide.onlineOnlyBlocked()).as(summary.code()).isEqualTo(!hasOnline);
        }
    }
}
