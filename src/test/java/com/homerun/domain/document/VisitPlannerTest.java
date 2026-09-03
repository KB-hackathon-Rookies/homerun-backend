package com.homerun.domain.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.document.dto.VisitPlanDtos.AgencyVisit;
import com.homerun.domain.document.dto.VisitPlanDtos.VisitPlan;
import com.homerun.domain.document.dto.VisitPlanDtos.VisitTask;
import com.homerun.domain.document.service.VisitPlanner;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VisitPlannerTest {

    private final VisitPlanner planner;

    VisitPlannerTest(@Autowired VisitPlanner planner) {
        this.planner = planner;
    }

    private static AgencyVisit visit(VisitPlan plan, String agency) {
        return plan.visits().stream()
                .filter(item -> item.agency().equals(agency))
                .findFirst()
                .orElseThrow(() -> new AssertionError("방문이 없다: " + agency));
    }

    // PLC-01-05 발급처별 업무 묶음

    @Test
    @DisplayName("온라인을 못 쓰면 같은 곳에서 끝나는 서류를 한 번의 방문으로 묶는다")
    void should_merge_documents_issued_at_the_same_place() {
        // 공동인증서나 프린터가 없는 경우다. 셋 다 주민센터에서 끝나므로 한 걸음이면 된다.
        //
        // 서류마다 최선을 따로 고르면 이게 안 된다. 등본과 가족관계증명서는 무인발급기가
        // 더 싸서 각자 그쪽을 고르고, 전입세대확인서만 창구로 남아 걸음이 둘이 된다.
        VisitPlan plan = planner.plan(List.of("RESIDENT_LIST", "RESIDENT_REGISTRATION", "FAMILY_RELATION"), false);

        assertThat(plan.visits()).hasSize(1);
        assertThat(visit(plan, "주민센터").tasks())
                .extracting(VisitTask::documentCode)
                .containsExactly("RESIDENT_LIST", "RESIDENT_REGISTRATION", "FAMILY_RELATION");
    }

    @Test
    @DisplayName("한 곳으로 못 묶으면 걸음 수를 최소로 나눈다")
    void should_minimize_visit_count_when_one_place_is_not_enough() {
        // 등기부는 법원, 나머지는 민원 쪽이라 한 곳으로는 안 된다. 그래도 둘이면 충분하다.
        VisitPlan plan = planner.plan(
                List.of("REGISTRY_CERT", "RESIDENT_LIST", "RESIDENT_REGISTRATION", "FAMILY_RELATION"), false);

        assertThat(plan.visits()).hasSize(2);
    }

    @Test
    @DisplayName("표기가 달라도 같은 걸음이면 묶인다")
    void should_group_by_normalized_agency_not_display_text() {
        // 지방세 납세증명과 소득금액증명은 발급처 표기가 '주민센터·구청' 과 '세무서·주민센터'
        // 로 달랐다. 표기로 묶으면 한 번에 끝날 일을 두 번 가게 만든다.
        VisitPlan plan = planner.plan(List.of("LOCAL_TAX_PAYMENT", "INCOME_CERT"), false);

        assertThat(plan.visits()).hasSize(1);
        assertThat(visit(plan, "주민센터").tasks()).hasSize(2);
    }

    @Test
    @DisplayName("이름이 비슷해도 다른 곳이면 따로 묶는다")
    void should_not_merge_different_kiosks() {
        // 등기부는 법원 무인발급기, 등본은 민원 무인발급기다. 둘 다 '무인발급기' 지만
        // 가는 곳이 다르다.
        VisitPlan plan = planner.plan(List.of("REGISTRY_CERT", "RESIDENT_REGISTRATION"), false);

        assertThat(plan.visits()).extracting(AgencyVisit::agency).containsExactlyInAnyOrder("법원 무인발급기", "무인민원발급기");
    }

    @Test
    @DisplayName("발급처가 다르면 방문을 나눈다")
    void should_split_visits_by_agency() {
        VisitPlan plan = planner.plan(List.of("RESIDENT_LIST", "EMPLOYMENT_CERT"));

        assertThat(plan.visits()).extracting(AgencyVisit::agency).containsExactlyInAnyOrder("주민센터", "재직 회사");
    }

    @Test
    @DisplayName("방문 목록은 넣은 순서를 지킨다")
    void should_keep_requested_order() {
        VisitPlan plan = planner.plan(List.of("EMPLOYMENT_CERT", "RESIDENT_LIST"), false);

        assertThat(plan.visits()).extracting(AgencyVisit::agency).containsExactly("재직 회사", "주민센터");
    }

    // 온라인 우선과 그 예외

    @Test
    @DisplayName("온라인으로 되면 방문으로 묶지 않는다")
    void should_prefer_online_by_default() {
        VisitPlan plan = planner.plan(List.of("RESIDENT_LIST", "RESIDENT_REGISTRATION", "FAMILY_RELATION"));

        assertThat(plan.onlineDocuments())
                .extracting(VisitTask::documentCode)
                .containsExactly("RESIDENT_REGISTRATION", "FAMILY_RELATION");
        assertThat(plan.visits()).hasSize(1);
        assertThat(visit(plan, "주민센터").tasks())
                .extracting(VisitTask::documentCode)
                .containsExactly("RESIDENT_LIST");
    }

    @Test
    @DisplayName("온라인을 못 쓰면 창구보다 무인발급기를 먼저 고른다")
    void should_prefer_kiosk_over_counter_when_offline() {
        // 등본은 무인 200원, 창구 400원이다. 더 싸고 24시간 되는 쪽이 먼저다.
        VisitPlan plan = planner.plan(List.of("RESIDENT_REGISTRATION"), false);

        assertThat(plan.visits()).extracting(AgencyVisit::agency).containsExactly("무인민원발급기");
        assertThat(plan.totalFee()).isEqualTo(200);
    }

    @Test
    @DisplayName("애초에 온라인이 안 되는 서류는 기본 설정에서도 방문이다")
    void should_visit_when_document_has_no_online_method() {
        VisitPlan plan = planner.plan(List.of("RESIDENT_LIST"));

        assertThat(plan.onlineDocuments()).isEmpty();
        assertThat(plan.visits()).extracting(AgencyVisit::agency).containsExactly("주민센터");
    }

    // 입력 다루기

    @Test
    @DisplayName("같은 서류를 두 번 적어도 한 번만 뗀다")
    void should_deduplicate_repeated_codes() {
        VisitPlan plan = planner.plan(List.of("RESIDENT_LIST", "RESIDENT_LIST"));

        assertThat(visit(plan, "주민센터").tasks()).hasSize(1);
    }

    @Test
    @DisplayName("모르는 서류 코드는 조용히 버리지 않는다")
    void should_report_unknown_codes() {
        VisitPlan plan = planner.plan(List.of("RESIDENT_LIST", "NO_SUCH_DOCUMENT"));

        assertThat(plan.unknownCodes()).containsExactly("NO_SUCH_DOCUMENT");
        assertThat(plan.visits()).hasSize(1);
    }

    @Test
    @DisplayName("서류를 하나도 안 넣으면 빈 계획이 나온다")
    void should_return_empty_plan_for_no_documents() {
        VisitPlan plan = planner.plan(List.of());

        assertThat(plan.visits()).isEmpty();
        assertThat(plan.onlineDocuments()).isEmpty();
        assertThat(plan.totalFee()).isZero();
    }

    // PLC-01-06 방문 체크리스트

    @Test
    @DisplayName("방문마다 챙길 것을 준다")
    void should_build_checklist_per_visit() {
        assertThat(visit(planner.plan(List.of("RESIDENT_LIST")), "주민센터").checklist())
                .contains("신분증")
                .anySatisfy(item -> assertThat(item).contains("계약서"));
    }

    @Test
    @DisplayName("여러 서류가 같은 준비물을 요구해도 한 번만 적는다")
    void should_not_repeat_shared_requirements() {
        AgencyVisit visit = visit(planner.plan(List.of("LOCAL_TAX_PAYMENT", "INCOME_CERT"), false), "주민센터");

        assertThat(visit.checklist()).filteredOn("신분증"::equals).hasSize(1);
    }

    @Test
    @DisplayName("방문 수수료를 합쳐서 보여 준다")
    void should_total_fees_per_visit() {
        // 주민등록등본 무인 200원 + 가족관계증명서 무인 500원
        AgencyVisit visit = visit(planner.plan(List.of("RESIDENT_REGISTRATION", "FAMILY_RELATION"), false), "무인민원발급기");

        assertThat(visit.totalFee()).isEqualTo(700);
    }

    @Test
    @DisplayName("전체 수수료는 방문 합계다. 온라인은 빠진다")
    void should_total_fees_across_visits() {
        VisitPlan plan = planner.plan(List.of("RESIDENT_LIST", "RESIDENT_REGISTRATION"));

        assertThat(plan.totalFee()).isEqualTo(400);
    }

    @Test
    @DisplayName("방문 전 확인사항을 함께 준다")
    void should_carry_notes_into_visit() {
        assertThat(visit(planner.plan(List.of("RESIDENT_LIST")), "주민센터").notes())
                .isNotEmpty();
    }

    // PLC-01-04 길찾기 연결

    @Test
    @DisplayName("갈 곳이 정해진 기관에는 길찾기 링크를 만든다")
    void should_link_directions_for_real_places() {
        assertThat(visit(planner.plan(List.of("RESIDENT_LIST")), "주민센터").directionsUrl())
                .startsWith("https://map.kakao.com/?q=");
    }

    @Test
    @DisplayName("회사는 가야 하지만 길찾기를 만들지 않는다")
    void should_not_link_directions_when_place_is_personal() {
        // 방문 여부와 길찾기 가능 여부를 한 플래그로 묶으면 회사 방문이 목록에서 사라진다.
        VisitPlan plan = planner.plan(List.of("EMPLOYMENT_CERT"));

        assertThat(plan.visits()).extracting(AgencyVisit::agency).containsExactly("재직 회사");
        assertThat(plan.visits().get(0).directionsUrl()).isNull();
    }

    // 이미 가지고 있는 것

    @Test
    @DisplayName("이미 가진 서류는 방문으로 세지 않는다")
    void should_not_count_already_held_document_as_a_visit() {
        // 임대차계약서는 계약할 때 받아 손에 있다. 이걸 방문으로 세면 갈 곳이 하나 늘어난다.
        VisitPlan plan = planner.plan(List.of("LEASE_AGREEMENT"));

        assertThat(plan.visits()).isEmpty();
        assertThat(plan.alreadyHeldDocuments())
                .extracting(VisitTask::documentCode)
                .containsExactly("LEASE_AGREEMENT");
        assertThat(plan.totalFee()).isZero();
    }

    @Test
    @DisplayName("이미 가진 것과 가야 할 곳을 함께 준다")
    void should_separate_already_held_from_visits() {
        VisitPlan plan = planner.plan(List.of("LEASE_AGREEMENT", "RESIDENT_LIST"));

        assertThat(plan.alreadyHeldDocuments()).hasSize(1);
        assertThat(plan.visits()).extracting(AgencyVisit::agency).containsExactly("주민센터");
    }
}
