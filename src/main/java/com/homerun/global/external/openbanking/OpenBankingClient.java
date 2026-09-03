package com.homerun.global.external.openbanking;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingResponses.Account;
import com.homerun.global.external.openbanking.OpenBankingResponses.Balance;
import com.homerun.global.external.openbanking.OpenBankingResponses.Loan;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanBasicPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.LoanTransaction;
import com.homerun.global.external.openbanking.OpenBankingResponses.Token;
import com.homerun.global.external.openbanking.OpenBankingResponses.Transaction;
import com.homerun.global.external.openbanking.OpenBankingResponses.TransactionPage;
import com.homerun.global.external.openbanking.OpenBankingResponses.UserInfo;
import java.math.BigDecimal;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenBankingClient {

    private static final DateTimeFormatter TRANSACTION_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TRANSACTION_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OpenBankingProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient restClient;

    @Autowired
    public OpenBankingClient(OpenBankingProperties properties, ObjectMapper objectMapper, Clock clock) {
        this(properties, objectMapper, clock, RestClient.create());
    }

    OpenBankingClient(OpenBankingProperties properties, ObjectMapper objectMapper, Clock clock, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.restClient = restClient;
    }

    public URI authorizationUri(String state) {
        validateConfiguration();
        return UriComponentsBuilder.fromUriString(properties.oauthBaseUrl())
                .path("/oauth/2.0/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", properties.scope())
                .queryParam("state", state)
                .queryParam("auth_type", "0")
                .build()
                .encode()
                .toUri();
    }

    public Token exchangeAuthorizationCode(String code) {
        validateConfiguration();
        MultiValueMap<String, String> form = commonTokenForm("authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        return requestToken(form);
    }

    public Token refreshToken(String refreshToken) {
        validateConfiguration();
        MultiValueMap<String, String> form = commonTokenForm("refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("scope", properties.scope());
        return requestToken(form);
    }

    public UserInfo userInfo(String accessToken, String userSeqNo) {
        URI uri = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path("/v2.0/user/me")
                .queryParam("user_seq_no", userSeqNo)
                .build()
                .encode()
                .toUri();
        JsonNode root = get(uri, accessToken);
        List<Account> accounts = new ArrayList<>();
        for (JsonNode account : root.path("res_list")) {
            accounts.add(new Account(
                    text(account, "account_alias"),
                    text(account, "bank_code_std"),
                    text(account, "bank_name"),
                    text(account, "savings_bank_name"),
                    text(account, "fintech_use_num"),
                    text(account, "account_num_masked"),
                    text(account, "account_holder_name"),
                    text(account, "account_type")));
        }
        return new UserInfo(text(root, "user_seq_no"), text(root, "user_name"), List.copyOf(accounts));
    }

    public Balance balance(String accessToken, String fintechUseNumber) {
        URI uri = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path("/v2.0/account/balance/fin_num")
                .queryParam("bank_tran_id", bankTransactionId())
                .queryParam("fintech_use_num", fintechUseNumber)
                .queryParam("tran_dtime", requestTime())
                .build()
                .encode()
                .toUri();
        JsonNode root = get(uri, accessToken);
        return new Balance(
                text(root, "bank_name"),
                text(root, "savings_bank_name"),
                text(root, "fintech_use_num"),
                decimal(root, "balance_amt"),
                decimal(root, "available_amt"),
                text(root, "account_type"),
                text(root, "product_name"),
                date(root, "account_issue_date"),
                date(root, "maturity_date"),
                date(root, "last_tran_date"),
                Instant.now(clock));
    }

    public TransactionPage transactions(
            String accessToken,
            String fintechUseNumber,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path("/v2.0/account/transaction_list/fin_num")
                .queryParam("bank_tran_id", bankTransactionId())
                .queryParam("fintech_use_num", fintechUseNumber)
                .queryParam("inquiry_type", "A")
                .queryParam("inquiry_base", "D")
                .queryParam("from_date", fromDate.format(TRANSACTION_DATE))
                .queryParam("to_date", toDate.format(TRANSACTION_DATE))
                .queryParam("sort_order", "D")
                .queryParam("tran_dtime", requestTime());
        if (beforeInquiryTraceInfo != null && !beforeInquiryTraceInfo.isBlank()) {
            uriBuilder.queryParam("befor_inquiry_trace_info", beforeInquiryTraceInfo);
        }
        JsonNode root = get(uriBuilder.build().encode().toUri(), accessToken);
        List<Transaction> transactions = new ArrayList<>();
        for (JsonNode transaction : root.path("res_list")) {
            String description = text(transaction, "print_content");
            if (description.isBlank()) {
                description = text(transaction, "printed_content");
            }
            transactions.add(new Transaction(
                    date(transaction, "tran_date"),
                    time(transaction, "tran_time"),
                    text(transaction, "inout_type"),
                    text(transaction, "tran_type"),
                    description,
                    decimal(transaction, "tran_amt"),
                    decimal(transaction, "after_balance_amt"),
                    text(transaction, "branch_name")));
        }
        return new TransactionPage(
                text(root, "bank_name"),
                text(root, "savings_bank_name"),
                text(root, "fintech_use_num"),
                decimal(root, "balance_amt"),
                "Y".equalsIgnoreCase(text(root, "next_page_yn")),
                text(root, "befor_inquiry_trace_info"),
                List.copyOf(transactions),
                Instant.now(clock));
    }

    public LoanPage loans(String accessToken, String userSeqNo, String bankCode, String beforeInquiryTraceInfo) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path("/v2.0/loans")
                .queryParam("bank_tran_id", bankTransactionId())
                .queryParam("user_seq_no", userSeqNo)
                .queryParam("bank_code_std", bankCode);
        addTraceInfo(uriBuilder, beforeInquiryTraceInfo);
        JsonNode root = get(uriBuilder.build().encode().toUri(), accessToken);
        List<Loan> loans = new ArrayList<>();
        String responseBankCode = defaultIfBlank(text(root, "bank_code_std"), bankCode);
        String responseBankName = text(root, "bank_name");
        for (JsonNode loan : root.path("loan_list")) {
            loans.add(new Loan(
                    defaultIfBlank(text(loan, "bank_code_std"), responseBankCode),
                    defaultIfBlank(text(loan, "bank_name"), responseBankName),
                    text(loan, "account_num"),
                    text(loan, "account_seq"),
                    text(loan, "account_num_masked"),
                    text(loan, "prod_name"),
                    text(loan, "account_type"),
                    text(loan, "account_status")));
        }
        return new LoanPage(
                hasNextPage(root), text(root, "befor_inquiry_trace_info"), List.copyOf(loans), Instant.now(clock));
    }

    public LoanBasicPage loanBasic(
            String accessToken,
            String userSeqNo,
            Loan loan,
            LocalDate fromDate,
            LocalDate toDate,
            String beforeInquiryTraceInfo) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path("/v2.0/loans/basic")
                .queryParam("bank_tran_id", bankTransactionId())
                .queryParam("bank_code_std", loan.bankCode())
                .queryParam("account_num", loan.accountNumber());
        if (!loan.accountSequence().isBlank()) {
            uriBuilder.queryParam("account_seq", loan.accountSequence());
        }
        uriBuilder
                .queryParam("user_seq_no", userSeqNo)
                .queryParam("from_date", fromDate.format(TRANSACTION_DATE))
                .queryParam("to_date", toDate.format(TRANSACTION_DATE));
        addTraceInfo(uriBuilder, beforeInquiryTraceInfo);
        JsonNode root = post(uriBuilder.build().encode().toUri(), accessToken);
        List<LoanTransaction> transactions = new ArrayList<>();
        for (JsonNode transaction : root.path("res_list")) {
            transactions.add(new LoanTransaction(
                    date(transaction, "trans_date"),
                    time(transaction, "trans_time"),
                    text(transaction, "trans_type"),
                    decimal(transaction, "trans_amt")));
        }
        return new LoanBasicPage(
                text(root, "repay_date"),
                text(root, "repay_method"),
                text(root, "repay_org_code"),
                date(root, "next_repay_date"),
                hasNextPage(root),
                text(root, "befor_inquiry_trace_info"),
                List.copyOf(transactions),
                Instant.now(clock));
    }

    private MultiValueMap<String, String> commonTokenForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    private Token requestToken(MultiValueMap<String, String> form) {
        try {
            String response = restClient
                    .post()
                    .uri(properties.oauthBaseUrl() + "/oauth/2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode root = readJson(response);
            if (!text(root, "error").isBlank()) {
                throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR);
            }
            return new Token(
                    requiredText(root, "access_token"),
                    text(root, "refresh_token"),
                    defaultIfBlank(text(root, "token_type"), "Bearer"),
                    text(root, "scope"),
                    text(root, "user_seq_no"),
                    longValue(root, "expires_in", 0),
                    nullableLong(root, "refresh_token_expires_in"));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR, exception);
        }
    }

    private JsonNode get(URI uri, String accessToken) {
        try {
            String response = restClient
                    .get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode root = readJson(response);
            String responseCode = text(root, "rsp_code");
            if (!responseCode.isBlank() && !"A0000".equals(responseCode)) {
                throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR);
            }
            return root;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR, exception);
        }
    }

    private JsonNode post(URI uri, String accessToken) {
        try {
            String response = restClient
                    .post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode root = readJson(response);
            String responseCode = text(root, "rsp_code");
            if (!responseCode.isBlank() && !"A0000".equals(responseCode)) {
                throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR);
            }
            return root;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR, exception);
        }
    }

    private void addTraceInfo(UriComponentsBuilder uriBuilder, String beforeInquiryTraceInfo) {
        if (beforeInquiryTraceInfo != null && !beforeInquiryTraceInfo.isBlank()) {
            uriBuilder.queryParam("befor_inquiry_trace_info", beforeInquiryTraceInfo);
        }
    }

    private boolean hasNextPage(JsonNode root) {
        return "Y".equalsIgnoreCase(text(root, "next_page_yn"));
    }

    private JsonNode readJson(String response) {
        if (response == null || response.isBlank()) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR);
        }
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR, exception);
        }
    }

    private void validateConfiguration() {
        if (isBlank(properties.oauthBaseUrl())
                || isBlank(properties.apiBaseUrl())
                || isBlank(properties.clientId())
                || isBlank(properties.clientSecret())
                || isBlank(properties.clientUseCode())
                || isBlank(properties.redirectUri())
                || isBlank(properties.scope())) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_CONFIGURATION_ERROR);
        }
        if (properties.clientUseCode().length() != 10) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_CONFIGURATION_ERROR);
        }
    }

    private String bankTransactionId() {
        validateConfiguration();
        return properties.clientUseCode() + "U" + String.format("%09d", SECURE_RANDOM.nextInt(1_000_000_000));
    }

    private String requestTime() {
        return Instant.now(clock).atZone(KOREA).format(REQUEST_TIME);
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : new BigDecimal(value);
    }

    private LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : LocalDate.parse(value, TRANSACTION_DATE);
    }

    private LocalTime time(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : LocalTime.parse(value, TRANSACTION_TIME);
    }

    private long longValue(JsonNode node, String field, long defaultValue) {
        String value = text(node, field);
        return value.isBlank() ? defaultValue : Long.parseLong(value);
    }

    private Long nullableLong(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : Long.valueOf(value);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value.isBlank() ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
