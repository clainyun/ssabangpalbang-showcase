package com.ssafy.ssabangpalbang.apartment.dataload;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "dataload")
public class DataLoadProperties {

    private List<String> sigunguCodes = new ArrayList<>();
    private List<String> dealYearMonths = new ArrayList<>();
    private long requestDelayMs = 120;
    private boolean dryRun;

    public List<String> getSigunguCodes() {
        return sigunguCodes;
    }

    public void setSigunguCodes(List<String> sigunguCodes) {
        this.sigunguCodes = sigunguCodes;
    }

    public List<String> getDealYearMonths() {
        return dealYearMonths;
    }

    public void setDealYearMonths(List<String> dealYearMonths) {
        this.dealYearMonths = dealYearMonths;
    }

    public long getRequestDelayMs() {
        return requestDelayMs;
    }

    public void setRequestDelayMs(long requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
