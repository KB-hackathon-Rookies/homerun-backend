package com.homerun.domain.property.service;

import com.homerun.domain.property.dto.response.LandlordConsentGuideResponse;
import com.homerun.domain.property.type.LandlordConsent;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 임대인 협조 상태별 안내(FR-P1-08). 판정이 아니라 안내라 상태만 받아 스크립트를 고른다.
 *
 * <p>REFUSED 여도 신호등을 RED 로 만들지 않는다(BR-10) — 여기서는 경고·설득 스크립트만 준다.
 * 스크립트는 NOT_ASKED/REFUSED 일 때만 노출한다.
 */
@Component
class LandlordConsentAdvisor {

    /** 아직 안 물어본 경우. 중개사를 통해 어떻게 물을지 알려준다. */
    private static final List<String> NOT_ASKED_SCRIPTS = List.of(
            "은행 상담 전에 임대인에게 전세대출 협조가 가능한지 확인해야 해요. 중개사에게 이렇게 물어보세요:",
            "\"세입자가 전세자금대출을 받을 예정인데, 집주인분이 질권설정·채권양도 통지 수령에 협조해 주실 수 있나요?\"");

    /** 거부한 경우. 4가지 오해에 대응하는 설득 스크립트(FR-COACH-05). */
    private static final List<String> REFUSED_SCRIPTS = List.of(
            "개인정보가 걱정되세요? — 은행은 보증금 반환채권 관계만 확인하고, 집주인의 다른 금융정보는 보지 않아요.",
            "집에 뭐가 걸리는 게 부담되세요? — 질권설정은 근저당처럼 집을 담보 잡는 게 아니라, 보증금 반환채권에만 걸리고 계약 종료 시 사라져요.",
            "절차가 복잡할까 봐요? — 통지서를 받고 확인해 주시면 되고, 대부분 서면·유선 한 번으로 끝나요.",
            "서류가 귀찮으세요? — 임대인이 준비할 서류는 거의 없고, 세입자와 은행이 대부분 처리해요.");

    private static final String BANK_WARNING = "임대인 협조를 아직 확인하지 않았어요. 은행 상담 전에 꼭 확인하세요 — 협조가 안 되면 대출이 진행되지 않아요.";
    private static final String BANK_WARNING_REFUSED = "임대인이 협조를 거부한 상태예요. 설득이 어려우면 다른 매물을 알아보는 편이 안전해요.";

    public LandlordConsentGuideResponse guide(LandlordConsent consent) {
        LandlordConsent state = consent == null ? LandlordConsent.NOT_ASKED : consent;
        return switch (state) {
            case CONFIRMED -> new LandlordConsentGuideResponse(state, null, List.of(), false);
            case NOT_ASKED -> new LandlordConsentGuideResponse(state, BANK_WARNING, NOT_ASKED_SCRIPTS, false);
            case REFUSED -> new LandlordConsentGuideResponse(state, BANK_WARNING_REFUSED, REFUSED_SCRIPTS, true);
        };
    }
}
