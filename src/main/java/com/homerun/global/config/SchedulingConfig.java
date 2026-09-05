package com.homerun.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 알림 컨슈머/리퍼/마감 스케줄러가 쓰는 @Scheduled 를 활성화한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
