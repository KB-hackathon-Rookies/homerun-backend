package com.homerun.global.external.fcm;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FCM 자격증명이 없을 때 쓰는 폴백. 실제 전송 없이 성공으로 처리해 파이프라인(스트림·인박스)만으로도
 * 앱이 부팅되고 데모가 돌아가게 한다.
 */
public class NoOpFcmSender implements FcmSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpFcmSender.class);

    @Override
    public FcmSendResult send(List<String> tokens, String title, String body, Map<String, String> data) {
        int count = tokens == null ? 0 : tokens.size();
        log.info("[FCM disabled] skip push: tokens={}, title={}", count, title);
        return new FcmSendResult(count, 0);
    }
}
