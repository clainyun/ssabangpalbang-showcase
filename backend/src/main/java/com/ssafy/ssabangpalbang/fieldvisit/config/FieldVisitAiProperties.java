package com.ssafy.ssabangpalbang.fieldvisit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.fieldvisit.ai")
public class FieldVisitAiProperties {

    /**
     * FastAPI base URL. Local default {@code http://localhost:8000},
     * Docker Compose {@code http://ai:8000}. Override with
     * {@code FIELD_VISIT_AI_BASE_URL}.
     */
    private String baseUrl = "http://localhost:8000";

    private String generatePath = "/internal/v1/checklists/generate";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(20);

    /** MVP에서는 자동 재시도하지 않는다. 설정만 외부화한다. */
    private int retryCount = 0;

    /**
     * 카탈로그 itemCode 선택 방식(AI-002). 기본 true로 항상 catalog 경로를 사용한다.
     * Override with {@code CHECKLIST_CATALOG_SELECTION_ENABLED}.
     */
    private boolean catalogSelectionEnabled = true;

    private String catalogClasspath =
            "classpath:data/checklist/ssabang_field_visit_checklist_raw_v3_300.json";

    private String selectPath = "/internal/v1/checklists/select";

    /** Gemini에 전달할 shortlist 크기(30~50). */
    private int catalogShortlistSize = 50;

    /** 최종 노출 목표 문항 수(20~30, 목표 25). */
    private int catalogTargetItemCount = 25;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getGeneratePath() {
        return generatePath;
    }

    public void setGeneratePath(String generatePath) {
        this.generatePath = generatePath;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String generatePath() {
        return generatePath;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public int retryCount() {
        return retryCount;
    }

    public boolean isCatalogSelectionEnabled() {
        return catalogSelectionEnabled;
    }

    public void setCatalogSelectionEnabled(boolean catalogSelectionEnabled) {
        this.catalogSelectionEnabled = catalogSelectionEnabled;
    }

    public boolean catalogSelectionEnabled() {
        return catalogSelectionEnabled;
    }

    public String getCatalogClasspath() {
        return catalogClasspath;
    }

    public void setCatalogClasspath(String catalogClasspath) {
        this.catalogClasspath = catalogClasspath;
    }

    public String catalogClasspath() {
        return catalogClasspath;
    }

    public String getSelectPath() {
        return selectPath;
    }

    public void setSelectPath(String selectPath) {
        this.selectPath = selectPath;
    }

    public String selectPath() {
        return selectPath;
    }

    public int getCatalogShortlistSize() {
        return catalogShortlistSize;
    }

    public void setCatalogShortlistSize(int catalogShortlistSize) {
        this.catalogShortlistSize = catalogShortlistSize;
    }

    public int catalogShortlistSize() {
        return catalogShortlistSize;
    }

    public int getCatalogTargetItemCount() {
        return catalogTargetItemCount;
    }

    public void setCatalogTargetItemCount(int catalogTargetItemCount) {
        this.catalogTargetItemCount = catalogTargetItemCount;
    }

    public int catalogTargetItemCount() {
        return catalogTargetItemCount;
    }
}
