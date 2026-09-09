package com.homerun.global.external.openbanking;

import com.homerun.domain.openbanking.type.Persona;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 페르소나 한 명의 계좌·거래·대출을 적어 둔 데모 픽스처 표.
 *
 * <p>따로 둔 이유는 정합성이다. 데모 모킹은 두 겹으로 되어 있다 — 조회 API 를 대신하는
 * {@link MockDataOpenBankingClient} 와, {@code financial_snapshot}·{@code plan_input} 에 값을 적재하는
 * {@code MockOpenBankingPlanSyncController} 다. 두 겹이 각자 숫자를 들고 있으면 자산확인 화면과 진단
 * 입력이 같은 사람을 두고 다른 이야기를 한다. 그래서 헤드라인 수치(금융자산·월소득·월지출·대출잔액·
 * 월상환액)는 {@link Persona} 한 곳에만 두고, 이 표는 그 수치를 <b>어떻게 나눠 담을지</b>만 정한다.
 *
 * <p>급여·대출상환 금액은 여기에 적지 않고 {@link Persona} 에서 그대로 가져온다. 계좌 잔액과 지출
 * 항목만 사람이 읽을 만한 조합으로 쪼개 두고, 합이 페르소나 값과 맞는지는 테스트가 지킨다. 숫자를
 * 만원 단위로 딱 떨어지게 두지 않은 것도 의도다 — 1,500만원을 500/500/500 으로 나누면 화면에서
 * 바로 가짜로 읽힌다.
 *
 * <p>월 현금흐름은 세 페르소나 모두 0 으로 닫는다: 급여 = 지출 + 본인 계좌 간 자동이체 + 대출상환.
 * 그래야 급여통장 잔액이 달마다 제자리라 어느 달을 조회해도 잔액 조회 결과와 어긋나지 않는다.
 */
final class MockPersonaFixtures {

    static final String BANK_CODE = "004";
    static final String BANK_NAME = "국민은행";
    static final String BRANCH_NAME = "샘플지점";

    /** 급여 입금. 요약 집계가 월소득을 잡아내는 유일한 표식이라 이 문자열이어야 한다. */
    static final String TYPE_SALARY = "급여";

    /**
     * 본인 명의 계좌 사이를 오가는 자동이체(적금·청약 납입). 출금과 입금이 짝을 이뤄 순자산이
     * 변하지 않으므로 월 지출 합계에서 빠진다. 고정지출은 {@code 자동이체} 로 따로 적어 섞이지
     * 않게 한다 — 둘을 같은 이름으로 쓰면 지출 합계가 납입액만큼 부풀어 페르소나 값과 어긋난다.
     */
    static final String TYPE_INTERNAL_TRANSFER = "이체";

    /** 대출 상환 출금. 페르소나가 월상환액을 지출과 따로 들고 있어 지출 합계에서도 빠진다. */
    static final String TYPE_DEBT_REPAYMENT = "대출상환";

    private static final Map<Persona, MockProfile> PROFILES = profiles();

    private MockPersonaFixtures() {}

    static MockProfile profile(Persona persona) {
        return PROFILES.get(persona);
    }

    static List<MockProfile> allProfiles() {
        return List.copyOf(PROFILES.values());
    }

    private static Map<Persona, MockProfile> profiles() {
        Map<Persona, MockProfile> profiles = new EnumMap<>(Persona.class);
        profiles.put(Persona.KIM_KUKMIN, kimKukmin());
        profiles.put(Persona.LEE_TIGHT, leeTight());
        profiles.put(Persona.PARK_SENIOR, parkSenior());
        return profiles;
    }

    /**
     * 시연 기본. 독립을 준비하는 평범한 사회초년생 — 월 실수령 250만원에 고정지출·카드로 150만원을
     * 쓰고 남는 100만원을 적금 90만·청약 10만으로 넘긴다. 대출이 없어 대출 목록은 비어 있다.
     */
    private static MockProfile kimKukmin() {
        Persona persona = Persona.KIM_KUKMIN;
        return new MockProfile(
                persona,
                List.of(
                        new MockAccount(
                                "SAMPLE-KIM-0001",
                                "샘플 급여통장",
                                "004-**-****-1234",
                                "1",
                                "샘플 직장인 우대통장",
                                amount(1_840_000),
                                List.of(
                                        expense(5, LocalTime.of(6, 0), "자동이체", "샘플 월세", 550_000),
                                        expense(14, LocalTime.of(12, 30), "카드", "샘플 생활비 체크카드", 430_000),
                                        expense(15, LocalTime.of(6, 0), "자동이체", "샘플 관리비", 120_000),
                                        expense(17, LocalTime.of(6, 0), "자동이체", "샘플 통신요금", 78_000),
                                        salary(persona),
                                        transferOut(LocalTime.of(6, 0), "샘플 자유적금 자동이체", 900_000),
                                        transferOut(LocalTime.of(6, 5), "샘플 주택청약 자동이체", 100_000),
                                        expense(28, LocalTime.of(13, 0), "카드", "샘플 신용카드 결제", 322_000))),
                        new MockAccount(
                                "SAMPLE-KIM-0002",
                                "샘플 자유적금",
                                "004-**-****-5678",
                                "2",
                                "샘플 자유적금",
                                amount(8_760_000),
                                List.of(transferIn(LocalTime.of(6, 0), "샘플 자유적금 납입", 900_000))),
                        new MockAccount(
                                "SAMPLE-KIM-0003",
                                "샘플 주택청약저축",
                                "004-**-****-9012",
                                "2",
                                "샘플 주택청약종합저축",
                                amount(4_400_000),
                                List.of(transferIn(LocalTime.of(6, 5), "샘플 주택청약 납입", 100_000)))),
                null);
    }

    /** 저소득·학자금. 소득요건은 되지만 자산이 얇아 부족자금이 크게 잡히는 대안 시나리오. */
    private static MockProfile leeTight() {
        Persona persona = Persona.LEE_TIGHT;
        return new MockProfile(
                persona,
                List.of(
                        new MockAccount(
                                "SAMPLE-LEE-0001",
                                "샘플 급여통장",
                                "004-**-****-2341",
                                "1",
                                "샘플 급여이체 통장",
                                amount(1_270_000),
                                List.of(
                                        expense(5, LocalTime.of(6, 0), "자동이체", "샘플 월세", 480_000),
                                        expense(12, LocalTime.of(12, 30), "카드", "샘플 생활비 체크카드", 384_000),
                                        expense(15, LocalTime.of(6, 0), "자동이체", "샘플 관리비", 90_000),
                                        expense(17, LocalTime.of(6, 0), "자동이체", "샘플 통신요금", 46_000),
                                        debtRepayment(persona, 20, "샘플 학자금대출 상환"),
                                        salary(persona),
                                        transferOut(LocalTime.of(6, 0), "샘플 자유적금 자동이체", 30_000),
                                        transferOut(LocalTime.of(6, 5), "샘플 주택청약 자동이체", 20_000),
                                        expense(28, LocalTime.of(13, 0), "카드", "샘플 신용카드 결제", 100_000))),
                        new MockAccount(
                                "SAMPLE-LEE-0002",
                                "샘플 자유적금",
                                "004-**-****-6785",
                                "2",
                                "샘플 자유적금",
                                amount(830_000),
                                List.of(transferIn(LocalTime.of(6, 0), "샘플 자유적금 납입", 30_000))),
                        new MockAccount(
                                "SAMPLE-LEE-0003",
                                "샘플 주택청약저축",
                                "004-**-****-0129",
                                "2",
                                "샘플 주택청약종합저축",
                                amount(900_000),
                                List.of(transferIn(LocalTime.of(6, 5), "샘플 주택청약 납입", 20_000)))),
                new MockLoan("SAMPLE-LEE-LOAN-0001", "004-**-****-2233", "샘플 학자금대출", 20));
    }

    /** 고소득 경계. 청년 버팀목 소득기준(연 5,000만)을 넘겨 일반 버팀목·SGI 로 우회하는 대안 시나리오. */
    private static MockProfile parkSenior() {
        Persona persona = Persona.PARK_SENIOR;
        return new MockProfile(
                persona,
                List.of(
                        new MockAccount(
                                "SAMPLE-PARK-0001",
                                "샘플 급여통장",
                                "004-**-****-3412",
                                "1",
                                "샘플 직장인 우대통장",
                                amount(4_730_000),
                                List.of(
                                        expense(5, LocalTime.of(6, 0), "자동이체", "샘플 월세", 750_000),
                                        expense(12, LocalTime.of(12, 30), "카드", "샘플 생활비 체크카드", 528_000),
                                        expense(15, LocalTime.of(6, 0), "자동이체", "샘플 관리비", 180_000),
                                        expense(17, LocalTime.of(6, 0), "자동이체", "샘플 통신요금", 92_000),
                                        salary(persona),
                                        transferOut(LocalTime.of(6, 0), "샘플 자유적금 자동이체", 1_700_000),
                                        transferOut(LocalTime.of(6, 5), "샘플 주택청약 자동이체", 100_000),
                                        debtRepayment(persona, 27, "샘플 신용대출 상환"),
                                        expense(28, LocalTime.of(13, 0), "카드", "샘플 신용카드 결제", 450_000))),
                        new MockAccount(
                                "SAMPLE-PARK-0002",
                                "샘플 자유적금",
                                "004-**-****-7856",
                                "2",
                                "샘플 자유적금",
                                amount(22_180_000),
                                List.of(transferIn(LocalTime.of(6, 0), "샘플 자유적금 납입", 1_700_000))),
                        new MockAccount(
                                "SAMPLE-PARK-0003",
                                "샘플 주택청약저축",
                                "004-**-****-1290",
                                "2",
                                "샘플 주택청약종합저축",
                                amount(13_090_000),
                                List.of(transferIn(LocalTime.of(6, 5), "샘플 주택청약 납입", 100_000)))),
                new MockLoan("SAMPLE-PARK-LOAN-0001", "004-**-****-7788", "샘플 신용대출", 27));
    }

    /** 급여일은 25일 하나로 고정한다. 금액은 페르소나에서 그대로 가져와 두 계층이 어긋날 여지를 없앤다. */
    private static MonthlyEntry salary(Persona persona) {
        return new MonthlyEntry(
                25, LocalTime.of(9, 10), Direction.DEPOSIT, TYPE_SALARY, "샘플급여", amount(persona.getMonthlyIncome()));
    }

    private static MonthlyEntry debtRepayment(Persona persona, int dayOfMonth, String description) {
        return new MonthlyEntry(
                dayOfMonth,
                LocalTime.of(6, 0),
                Direction.WITHDRAWAL,
                TYPE_DEBT_REPAYMENT,
                description,
                amount(persona.getMonthlyDebtPayment()));
    }

    private static MonthlyEntry expense(int dayOfMonth, LocalTime time, String type, String description, long amount) {
        return new MonthlyEntry(dayOfMonth, time, Direction.WITHDRAWAL, type, description, amount(amount));
    }

    /** 적금·청약 납입은 26일에 급여통장에서 나가 같은 날 같은 금액으로 저축계좌에 들어온다. */
    private static MonthlyEntry transferOut(LocalTime time, String description, long amount) {
        return new MonthlyEntry(26, time, Direction.WITHDRAWAL, TYPE_INTERNAL_TRANSFER, description, amount(amount));
    }

    private static MonthlyEntry transferIn(LocalTime time, String description, long amount) {
        return new MonthlyEntry(26, time, Direction.DEPOSIT, TYPE_INTERNAL_TRANSFER, description, amount(amount));
    }

    private static BigDecimal amount(long value) {
        return BigDecimal.valueOf(value);
    }

    enum Direction {
        DEPOSIT("입금"),
        WITHDRAWAL("출금");

        private final String label;

        Direction(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record MonthlyEntry(
            int dayOfMonth, LocalTime time, Direction direction, String type, String description, BigDecimal amount) {

        BigDecimal signedAmount() {
            return direction == Direction.DEPOSIT ? amount : amount.negate();
        }
    }

    record MockAccount(
            String fintechUseNumber,
            String alias,
            String accountNumberMasked,
            String accountType,
            String productName,
            BigDecimal balance,
            List<MonthlyEntry> monthlyEntries) {}

    /**
     * 대출 표시 정보만 담는다. 대출잔액·월상환액은 {@link Persona} 가 들고 있다 — 오픈뱅킹 대출
     * 응답에는 잔액 필드가 없어 이쪽에 옮겨 적어 봐야 어긋날 자리만 하나 더 생긴다.
     */
    record MockLoan(String accountNumber, String accountNumberMasked, String productName, int repaymentDay) {}

    record MockProfile(Persona persona, List<MockAccount> accounts, MockLoan loan) {

        String holderName() {
            return persona.getLabel();
        }
    }
}
