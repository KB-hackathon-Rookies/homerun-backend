package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.ContractChecklistGuideResponse;
import com.homerun.domain.contract.dto.response.ContractChecklistGuideResponse.Item;
import com.homerun.domain.contract.type.ContractChecklistPhase;
import java.util.List;
import org.springframework.stereotype.Service;

/** 개인 계약의 판정(PreContractChecklist)과 분리된 COM-12-01 상시 안내. */
@Service
public class ContractChecklistGuideService {

    private static final String IROS = "https://www.iros.go.kr";
    private static final String GOV24 = "https://www.gov.kr";
    private static final String HUG = "https://onestop.khug.or.kr/";
    private static final List<Item> ITEMS = List.of(
            new Item(
                    "REGISTRY",
                    ContractChecklistPhase.BEFORE_CONTRACT,
                    1,
                    "등기사항전부증명서 확인",
                    "주소·소유자와 근저당·신탁 등 권리관계를 확인하세요.",
                    "내용을 이해하기 어렵다면 계약 전에 공인중개사나 전문가에게 확인하세요.",
                    "REGISTRY_CERT",
                    "인터넷등기소",
                    IROS),
            new Item(
                    "BUILDING_LEDGER",
                    ContractChecklistPhase.BEFORE_CONTRACT,
                    2,
                    "건축물대장 확인",
                    "계약하려는 호실의 용도·면적·위반건축물 표시를 확인하세요.",
                    "현장과 서류의 내용이 다르면 원인을 확인한 뒤 계약 여부를 결정하세요.",
                    "BUILDING_LEDGER",
                    "정부24",
                    GOV24),
            new Item(
                    "BANK_CONSULT",
                    ContractChecklistPhase.BEFORE_CONTRACT,
                    3,
                    "대출·보증 사전 확인",
                    "대출이나 반환보증을 이용한다면 해당 매물의 취급 가능 여부를 은행·보증기관에 문의하세요.",
                    "사전 안내는 최종 대출 승인이나 보증 가입을 보장하지 않습니다. 이용하지 않으면 참고만 하세요.",
                    null,
                    "HUG 안심전세",
                    HUG),
            new Item(
                    "CONTRACT_CONTENT",
                    ContractChecklistPhase.CONTRACT_DAY,
                    4,
                    "계약 당사자와 계약 내용 확인",
                    "임대인 또는 대리인의 권한, 주소, 보증금·월세, 지급일과 지급 계좌를 대조하세요.",
                    "확인되지 않은 대리 권한이나 설명이 다른 계약 조건은 서명 전에 확인하세요.",
                    "LEASE_AGREEMENT",
                    "HUG 안심전세",
                    HUG),
            new Item(
                    "SPECIAL_CLAUSE",
                    ContractChecklistPhase.CONTRACT_DAY,
                    5,
                    "특약 협의와 증빙 보관",
                    "대출 불승인 등 우려 상황의 처리 조건을 협의하고 계약서·지급 증빙을 보관하세요.",
                    "특약의 적용과 효력은 문구와 계약 사정에 따라 달라집니다. 자동 반환을 보장하는 안내가 아닙니다.",
                    "LEASE_AGREEMENT",
                    "HUG 안심전세",
                    HUG),
            new Item(
                    "FIXED_DATE",
                    ContractChecklistPhase.CONTRACT_DAY,
                    6,
                    "확정일자 처리 확인",
                    "계약 후 확정일자 부여 방법과 처리 여부를 확인하세요.",
                    "확정일자만으로 모든 보증금 보호 요건이 충족되는 것은 아닙니다.",
                    "LEASE_AGREEMENT",
                    "인터넷등기소",
                    IROS),
            new Item(
                    "BALANCE_RECHECK",
                    ContractChecklistPhase.MOVE_IN_DAY,
                    7,
                    "잔금 지급 전 재확인",
                    "등기 내용의 변경, 지급 계좌와 주택 인도 준비 상태를 다시 확인하고 지급 증빙을 남기세요.",
                    "계약 당시 확인한 내용만으로 잔금일의 상태를 판단하지 마세요.",
                    "REGISTRY_CERT",
                    "인터넷등기소",
                    IROS),
            new Item(
                    "MOVE_IN_REPORT",
                    ContractChecklistPhase.MOVE_IN_DAY,
                    8,
                    "입주·전입신고 확인",
                    "실제 입주에 맞춰 전입신고 절차를 확인하고 확정일자 처리 여부도 다시 확인하세요.",
                    "신고 대상·방법과 권리 보호 요건은 공식 안내를 확인하세요.",
                    null,
                    "정부24",
                    GOV24));

    public ContractChecklistGuideResponse get(ContractChecklistPhase phase) {
        return new ContractChecklistGuideResponse(
                "1.0",
                "일반적인 전월세 계약 확인 안내입니다. 개인별 완료 상태나 계약 안전성을 판정하지 않으며, 최신 공식 안내와 전문가 확인이 필요합니다.",
                ITEMS.stream()
                        .filter(item -> phase == null || item.phase() == phase)
                        .toList());
    }
}
