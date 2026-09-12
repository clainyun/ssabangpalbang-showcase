package com.ssafy.ssabangpalbang.demoseed;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDateTime;

/**
 * 유저 테스트(시연)용 데이터 시더 설정.
 *
 * <p>{@code demoseed} 프로파일에서만 로드된다. 프로파일이 없으면 빈으로도 등록되지 않아
 * 평소 기동에 영향이 없다({@code ApartmentDataLoadRunner}와 같은 방식).</p>
 */
@ConfigurationProperties(prefix = "demoseed")
public class DemoSeedProperties {

    /** 도곡렉슬. 이름이 아니라 국토부 단지코드로 찾는다 — 환경마다 id가 달라도 안전하다. */
    private String apartmentComplexCode = "A13527203";

    /**
     * 계정 5개 공통 비밀번호. 기본값이 없으므로 반드시 외부에서 지정해야 한다.
     * 예: {@code --demoseed.password=<값>} 또는 환경변수 {@code DEMOSEED_PASSWORD}.
     * 비어 있으면 {@link DemoSeedRunner}가 시딩을 중단한다.
     */
    private String password = "";

    /**
     * 시연용 스터디 5개에 넣을 임장 일정 시각(KST). 5개 팀 모두 같은 시각이다.
     *
     * <p>일정이 없으면 홈 화면이 "예정된 임장이 없어요"로 비고, 임장 시작 때 "예정된 임장
     * 일정이 없습니다" 확인 다이얼로그가 한 단계 더 뜬다. 시연 동선을 매끄럽게 하려고 넣는다.</p>
     */
    private LocalDateTime fieldVisitAt = LocalDateTime.of(2026, 8, 11, 14, 0);

    /** 임장 일정 길이(시간). endAt = fieldVisitAt + 이 값. */
    private int fieldVisitDurationHours = 2;

    /** 임장 일정의 집결 장소. */
    private String meetingPlace = "도곡렉슬 정문";

    /** 리포트를 담을 과거 임장 스터디 수. 시연용 5개 스터디와는 별개다. */
    private int reportStudyCount = 3;

    /** 적재한 리포트를 AI 서비스에 색인 요청할지. 끄면 챗봇이 리포트를 근거로 쓰지 못한다. */
    private boolean indexReports = true;

    /** true 이면 시딩 전에 앱 데이터를 전부 지운다. resetToken 이 함께 맞아야 실행된다. */
    private boolean reset;

    /** 오조작 방지용 확인 토큰. reset=true 일 때 이 값이 정확히 일치해야 한다. */
    private String resetToken = "";

    public String getApartmentComplexCode() {
        return apartmentComplexCode;
    }

    public void setApartmentComplexCode(String apartmentComplexCode) {
        this.apartmentComplexCode = apartmentComplexCode;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getFieldVisitAt() {
        return fieldVisitAt;
    }

    public void setFieldVisitAt(LocalDateTime fieldVisitAt) {
        this.fieldVisitAt = fieldVisitAt;
    }

    public int getFieldVisitDurationHours() {
        return fieldVisitDurationHours;
    }

    public void setFieldVisitDurationHours(int fieldVisitDurationHours) {
        this.fieldVisitDurationHours = fieldVisitDurationHours;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public void setMeetingPlace(String meetingPlace) {
        this.meetingPlace = meetingPlace;
    }

    public int getReportStudyCount() {
        return reportStudyCount;
    }

    public void setReportStudyCount(int reportStudyCount) {
        this.reportStudyCount = reportStudyCount;
    }

    public boolean isIndexReports() {
        return indexReports;
    }

    public void setIndexReports(boolean indexReports) {
        this.indexReports = indexReports;
    }

    public boolean isReset() {
        return reset;
    }

    public void setReset(boolean reset) {
        this.reset = reset;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
}
