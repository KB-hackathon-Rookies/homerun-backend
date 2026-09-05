-- 검수를 마친 최신 버전을 ACTIVE로 승격한다(#140). 지금까지 어떤 마이그레이션도
-- policy_rule.status를 ACTIVE로 바꾼 적이 없어, PolicyRuleEngine이 실제로는 판정할
-- 규칙을 하나도 못 찾는 상태였다(findFirstByPolicyIdAndStatusOrderByVersionDesc(...,
-- ACTIVE)). 엔진이 지원하지 않는 op를 쓰는 버전이나 참조 fact가 UNKNOWN/CONFLICT인
-- 버전은 대상에서 뺐다 — 승격 기준과 정책별 근거는 이슈(#140) 본문 참고.
--
-- YOUTH-FUTURE-SAVINGS v1의 HOUSEHOLD_INCOME_RATIO/EXCLUSION_CHECK는 external_check로
-- 설계상 항상 NEED_INFO다. 이는 버그가 아니라 외부 확인이 실제로 필요한 항목이라 승격
-- 대상에 포함한다 — 승격 안 하면 이 정책 자체를 판정할 수 없게 된다.
UPDATE policy_rule SET status = 'ACTIVE'
WHERE (policy_id, version) IN (
    ((SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK'), 3),
    ((SELECT id FROM policy WHERE code = 'JEONSE-GENERAL-BEOTIMMOK'), 3),
    ((SELECT id FROM policy WHERE code = 'JEONSE-SEOUL-INTEREST-SUPPORT'), 3),
    ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-HUG'), 1),
    ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-HF'), 1),
    ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-SGI'), 1),
    ((SELECT id FROM policy WHERE code = 'RETURN-GUARANTEE-FEE-SUPPORT'), 1),
    ((SELECT id FROM policy WHERE code = 'YOUTH-FUTURE-SAVINGS'), 1)
);
