package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.UnreturnedDepositResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 보증금 미반환 대응을 안내한다(FR-HX-02·03·BR-33). 판정이 아니라 정해진 순서 안내다.
 *
 * <p>핵심은 순서다 — 새 집으로 전입신고를 옮기면 대항력·우선변제권이 사라져 회복할 수 없다.
 * 그래서 전입신고 유지 경고를 최우선으로 두고, 반환보증 가입 여부로 이후 절차를 분기한다.
 */
@Component
class UnreturnedDepositAdvisor {

    private static final String TOP_WARNING =
            "새 집으로 전입신고를 옮기지 마세요. 전입을 빼는 순간 대항력·우선변제권이 사라져 보증금을 지킬 수 없어요." + " 이사가 불가피하면 임차권등기명령 완료를 확인한 뒤에만 전입하세요.";
    private static final String DO_NOT_SKIP = "단계를 건너뛰면 권리를 잃어 회복할 수 없어요. 순서대로 진행하세요.";
    private static final List<String> COUNSELING = List.of(
            "서울시 전월세종합지원센터", "대한법률구조공단 132", "HUG 콜센터 1566-9009", "주택도시기금 1599-1771", "전세피해확인서(저리 기금대출·긴급 주거지원) 신청 안내");

    public UnreturnedDepositResponse guide(boolean hasReturnGuarantee) {
        List<String> steps = new ArrayList<>();
        steps.add("① 임차권등기명령을 신청하세요(관할 법원, 계약서·등기부 지참, 등록면허세·수수료). 완료 확인 후에만 이사하세요.");
        if (hasReturnGuarantee) {
            // 반환보증 가입자는 보증기관에 이행청구로 끝난다.
            steps.add("② 반환보증에 가입돼 있으니 보증기관(HUG/HF/SGI)에 보증이행을 청구하세요. 여기서 절차가 끝나요.");
        } else {
            steps.add("② 임대인에게 내용증명을 보내세요.");
            steps.add("③ 보증금반환청구소송을 제기하세요.");
            steps.add("④ 승소 후에도 반환하지 않으면 강제집행을 신청하세요.");
        }
        return new UnreturnedDepositResponse(TOP_WARNING, hasReturnGuarantee, steps, COUNSELING, DO_NOT_SKIP);
    }
}
