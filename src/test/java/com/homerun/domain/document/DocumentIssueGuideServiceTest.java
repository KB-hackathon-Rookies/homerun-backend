package com.homerun.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.document.dto.DocumentDtos.DocumentIssueGuide;
import com.homerun.domain.document.dto.DocumentDtos.DocumentSummary;
import com.homerun.domain.document.dto.DocumentDtos.IssueMethodView;
import com.homerun.domain.document.repository.DocumentTypeRepository;
import com.homerun.domain.document.service.DocumentIssueGuideService;
import com.homerun.domain.document.type.IssueMethod;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DocumentIssueGuideServiceTest {

    private final DocumentIssueGuideService service;
    private final DocumentTypeRepository documents;

    DocumentIssueGuideServiceTest(
            @Autowired DocumentIssueGuideService service, @Autowired DocumentTypeRepository documents) {
        this.service = service;
        this.documents = documents;
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
        // 주민등록법 시행규칙 — 무인발급기는 방문 교부의 절반이다.
        assertThat(method(guide, IssueMethod.KIOSK).fee()).isEqualTo(200);
        assertThat(method(guide, IssueMethod.VISIT).fee()).isEqualTo(400);
    }

    @Test
    @DisplayName("등기부는 무인발급기와 방문 수수료가 다르다")
    void should_separate_registry_kiosk_from_visit() {
        DocumentIssueGuide guide = service.guide("REGISTRY_CERT");

        // 등기사항증명서 등 수수료규칙 — 인터넷·무인 1,000원, 방문 1,200원.
        assertThat(method(guide, IssueMethod.ONLINE).fee()).isEqualTo(1000);
        assertThat(method(guide, IssueMethod.KIOSK).fee()).isEqualTo(1000);
        assertThat(method(guide, IssueMethod.VISIT).fee()).isEqualTo(1200);
    }

    @Test
    @DisplayName("전입세대확인서는 열람가가 아니라 교부 수수료를 안내한다")
    void should_quote_issuance_fee_not_inspection_fee() {
        IssueMethodView visit = method(service.guide("RESIDENT_LIST"), IssueMethod.VISIT);

        // FCT-100 의 300원은 열람 수수료다. 제출용은 교부라 400원부터다.
        assertThat(visit.fee()).isEqualTo(400);
        assertThat(visit.feeNote()).contains("열람");
    }

    @Test
    @DisplayName("지역마다 갈리는 수수료는 갈린다는 사실을 함께 알린다")
    void should_flag_locally_varying_fee() {
        // 정부24 안내가 무인발급기 수수료는 자치단체 조례에 따라 달라진다고만 말한다.
        // 전국 공통 카탈로그가 하나로 못 박으면 지역에 따라 틀린 안내가 된다.
        IssueMethodView kiosk = method(service.guide("BUILDING_LEDGER"), IssueMethod.KIOSK);

        assertThat(kiosk.feeNote()).contains("자치단체");
    }

    @Test
    @DisplayName("수수료가 조건에 따라 갈리면 그 조건을 함께 준다")
    void should_explain_conditional_fee() {
        assertThat(method(service.guide("RESIDENT_REGISTRATION"), IssueMethod.VISIT)
                        .feeNote())
                .isNotBlank();
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
                    assertThat(online.url()).as(summary.code()).startsWith("https://");
                });
    }

    @Test
    @DisplayName("정부24 링크는 포털 홈이 아니라 해당 민원 화면으로 간다")
    void should_deep_link_into_gov_kr_service_page() {
        // 정부24 는 민원이 수천 개라 홈으로 보내면 사용자가 거기서 다시 검색해야 한다.
        assertThat(service.list().documents())
                .filteredOn(DocumentSummary::onlineAvailable)
                .allSatisfy(summary -> {
                    String url = method(service.guide(summary.code()), IssueMethod.ONLINE)
                            .url();
                    if (url.contains("gov.kr")) {
                        assertThat(url).as(summary.code()).contains("CappBizCD=");
                    }
                });
    }

    @Test
    @DisplayName("최상위 주소로 보내는 곳은 메뉴 경로를 함께 준다")
    void should_give_menu_path_when_url_is_a_site_root() {
        // 홈택스·위택스는 신고·납부·상담까지 하는 종합 사이트다. 최상위로 보내면 사용자가
        // 메뉴를 다시 찾아야 하므로, 직접 링크가 없으면 경로라도 적어 준다.
        assertThat(service.list().documents())
                .filteredOn(DocumentSummary::onlineAvailable)
                .allSatisfy(summary -> {
                    IssueMethodView online = method(service.guide(summary.code()), IssueMethod.ONLINE);
                    boolean deepLink =
                            online.url().replaceFirst("^https://[^/]+", "").length() > 1;

                    if (!deepLink) {
                        assertThat(online.note()).as(summary.code()).contains(">");
                    }
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

    // 서류 유효기간

    @Test
    @DisplayName("근거가 있는 서류에만 인정 기간을 넣는다")
    void should_set_validity_only_where_grounded() {
        // FCT-112 의 적용 조건은 등기부·주민등록등본·가족관계증명서 셋뿐이다.
        for (String code : List.of("REGISTRY_CERT", "RESIDENT_REGISTRATION", "FAMILY_RELATION")) {
            assertThat(service.guide(code).validityDays()).as(code).isEqualTo(30);
        }
    }

    @Test
    @DisplayName("근거가 없으면 기간을 지어내지 않고 확인하라고 알린다")
    void should_not_invent_validity_without_ground() {
        DocumentIssueGuide guide = service.guide("INCOME_CERT");

        assertThat(guide.validityDays()).isNull();
        assertThat(guide.validityNote()).isNotBlank();
    }

    @Test
    @DisplayName("인정 기간을 아는 서류에는 확인 안내를 붙이지 않는다")
    void should_omit_validity_note_when_known() {
        assertThat(service.guide("REGISTRY_CERT").validityNote()).isNull();
    }

    // 시드 정합성

    @Test
    @DisplayName("document_type 의 대표 수수료가 발급방법의 최저가와 어긋나지 않는다")
    void should_keep_catalog_fee_consistent_with_methods() {
        // 응답만 비교하면 양쪽 다 document_issue_method 에서 나오므로 document_type.fee 가
        // 아무 값이어도 통과한다. 시드 컬럼을 직접 읽어야 실제로 확인이 된다.
        for (DocumentSummary summary : service.list().documents()) {
            int cheapest = service.guide(summary.code()).methods().stream()
                    .mapToInt(IssueMethodView::fee)
                    .min()
                    .orElse(0);
            int catalogFee = documents.findByCode(summary.code()).orElseThrow().getFee();

            assertThat(catalogFee).as(summary.code()).isEqualTo(cheapest);
            assertThat(summary.cheapestFee()).as(summary.code()).isEqualTo(cheapest);
        }
    }

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
