package com.ssafy.ssabangpalbang.demoseed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 유저 테스트(시연)용 데이터를 적재한다. 모든 단계가 <b>멱등</b>하다 — 여러 번 돌려도
 * 같은 상태로 수렴한다. 판정 키는 계정 email, 스터디 title, 게시글 (작성자, title)이다.
 *
 * <p>적재 대상은 {@code _workspace/demo-apk/CHECKLIST.md} §2·§4가 정본이다.</p>
 */
@Service
@Profile("demoseed")
public class DemoSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    /** 리포트를 담을 과거 임장 스터디 제목 접두어. 시연용 팀 스터디와 구분된다. */
    private static final String REPORT_STUDY_TITLE_FORMAT = "도곡렉슬 임장 기록 #%d";

    /** 임장 일정 시각은 KST 기준으로 입력받는다. 저장은 Instant(TIMESTAMPTZ)다. */
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final DemoSeedProperties properties;
    private final DemoSeedReportIndexer reportIndexer;

    public DemoSeedService(
            EntityManager entityManager,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            DemoSeedProperties properties,
            DemoSeedReportIndexer reportIndexer
    ) {
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.reportIndexer = reportIndexer;
    }

    /**
     * @return 색인 요청 대상 리포트 id 목록. 트랜잭션이 커밋된 뒤에 색인해야 하므로 밖으로 넘긴다.
     */
    @Transactional
    public List<Long> seed() {
        Apartment apartment = findApartment();
        log.info(
                "[demoseed] 대상 아파트: id={} name={} ({}, {})",
                apartment.getId(),
                apartment.getName(),
                apartment.getLatitude(),
                apartment.getLongitude()
        );

        List<Member> members = seedMembers();
        Long leaderId = members.get(0).getId();

        seedTeamStudies(apartment.getId(), leaderId, members);
        List<Long> reportIds = seedReportStudies(apartment.getId(), leaderId, members);
        seedPosts(apartment.getId(), members);

        return reportIds;
    }

    /** 트랜잭션 밖에서 호출한다 — 색인은 외부 HTTP 호출이라 DB 트랜잭션을 붙잡고 있으면 안 된다. */
    public void indexReports(List<Long> reportIds) {
        if (!properties.isIndexReports()) {
            log.warn("[demoseed] indexReports=false 이므로 RAG 색인을 건너뜁니다. 챗봇은 리포트를 근거로 쓰지 못합니다.");
            return;
        }
        reportIndexer.indexAll(reportIds);
    }

    // ---------------------------------------------------------------- 아파트

    private Apartment findApartment() {
        String complexCode = properties.getApartmentComplexCode();
        List<Apartment> found = entityManager
                .createQuery(
                        "select a from Apartment a where a.complexCode = :code",
                        Apartment.class
                )
                .setParameter("code", complexCode)
                .getResultList();

        if (found.isEmpty()) {
            throw new IllegalStateException(
                    "대상 아파트를 찾을 수 없습니다. complexCode=" + complexCode
                            + " — 아파트 데이터가 적재됐는지 확인하십시오(INF-004)."
            );
        }
        Apartment apartment = found.get(0);
        if (apartment.getLatitude() == null || apartment.getLongitude() == null) {
            throw new IllegalStateException(
                    "대상 아파트에 좌표가 없습니다. apartmentId=" + apartment.getId()
                            + " — 좌표가 없으면 임장 시작이 서버에서 실패합니다."
            );
        }
        return apartment;
    }

    // ---------------------------------------------------------------- 계정

    private List<Member> seedMembers() {
        String passwordHash = passwordEncoder.encode(properties.getPassword());
        List<Member> members = new ArrayList<>();

        for (DemoSeedCatalog.Account account : DemoSeedCatalog.ACCOUNTS) {
            Member member = findMemberByEmail(account.email());
            if (member == null) {
                member = new Member(account.email(), passwordHash, account.nickname());
                entityManager.persist(member);
                log.info("[demoseed] 계정 생성: {} ({})", account.email(), account.nickname());
            } else {
                log.info("[demoseed] 계정 재사용: {}", account.email());
            }

            member.updateProfile(
                    account.nickname(),
                    account.ageGroup(),
                    account.ageGroupPublicAgreed(),
                    account.characterId()
            );
            entityManager.flush();

            seedPreference(member.getId(), account);
            members.add(member);
        }
        return members;
    }

    private Member findMemberByEmail(String email) {
        return entityManager
                .createQuery("select m from Member m where m.email = :email", Member.class)
                .setParameter("email", email)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 온보딩 응답. {@code MemberPreference} 행의 존재가 곧 온보딩 완료 판정이므로
     * (AuthService 의 existsPreferenceByMemberId) 이걸 넣지 않으면 로그인 후 온보딩이 다시 뜬다.
     */
    private void seedPreference(Long memberId, DemoSeedCatalog.Account account) {
        MemberPreference preference = entityManager
                .createQuery(
                        "select p from MemberPreference p where p.memberId = :memberId",
                        MemberPreference.class
                )
                .setParameter("memberId", memberId)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);

        if (preference == null) {
            preference = new MemberPreference(memberId);
            entityManager.persist(preference);
        }
        preference.updateOnboarding(
                account.purpose(),
                account.maritalStatus(),
                account.hasVehicle(),
                account.hasChildren(),
                account.priorities()
        );
    }

    // ---------------------------------------------------------------- 시연 스터디

    /**
     * 시연용 스터디 5개. 계정 5개 전부가 ACTIVE 멤버로 들어가고 usertest1 이 전부의 스터디장이다.
     *
     * <p>상태를 <b>CLOSED(모집 마감)</b>로 만든다. {@code Study.markInProgress()}가 CLOSED가
     * 아니면 예외를 던지므로, RECRUITING 상태로 두면 임장 시작 자체가 불가능하다.</p>
     */
    private void seedTeamStudies(Long apartmentId, Long leaderId, List<Member> members) {
        Instant closedAt = Instant.now().minus(Duration.ofDays(1));

        for (DemoSeedCatalog.TeamStudy definition : DemoSeedCatalog.TEAM_STUDIES) {
            Study study = findStudyByTitle(definition.title());
            if (study == null) {
                study = Study.create(
                        apartmentId,
                        leaderId,
                        definition.title(),
                        definition.intro(),
                        definition.goal(),
                        DemoSeedCatalog.STUDY_CAPACITY,
                        definition.purpose()
                );
                entityManager.persist(study);
                entityManager.flush();
                log.info("[demoseed] 스터디 생성: {} (id={})", definition.title(), study.getId());
            } else {
                log.info("[demoseed] 스터디 재사용: {} (id={})", definition.title(), study.getId());
            }

            // 이미 임장이 진행·완료된 스터디는 되돌리지 않는다. 아직 모집 중이면 마감 상태로 만든다.
            switch (study.getStatus()) {
                case RECRUITING -> study.closeRecruitment(closedAt);
                case CLOSED, IN_PROGRESS, COMPLETED -> { }
                case CANCELED -> log.warn(
                        "[demoseed] 취소된 스터디입니다. 리셋 후 다시 시딩하십시오. id={}",
                        study.getId()
                );
            }

            seedStudyMembers(study.getId(), leaderId, members);
            seedSchedule(study.getId(), definition.title());
        }
    }

    /**
     * 임장 일정. 5개 팀 모두 같은 시각이다 ({@code demoseed.field-visit-at}, 기본 2026-08-11 14:00 KST).
     *
     * <p>일정이 없으면 홈이 "예정된 임장이 없어요"로 비고, 임장 시작 때 "예정된 임장 일정이
     * 없습니다" 확인 다이얼로그가 한 단계 더 뜬다 — 2026-08-06 에뮬레이터 실측으로 확인했다.</p>
     */
    private void seedSchedule(Long studyId, String studyTitle) {
        Instant startAt = properties.getFieldVisitAt().atZone(SEOUL_ZONE).toInstant();
        Instant endAt = startAt.plus(Duration.ofHours(properties.getFieldVisitDurationHours()));

        // schedule.study_id 가 UNIQUE 다 — 스터디당 일정은 최대 1개다(V1 스키마 209행).
        // 그래서 새로 만들지 말고 upsert 한다. 임장을 시작하면 서버가 즉시 일정을
        // 자동 생성하므로(meeting_place='현장'), 리허설을 한 스터디에는 이미 행이 있다.
        Schedule existing = entityManager
                .createQuery(
                        "select s from Schedule s where s.studyId = :studyId",
                        Schedule.class
                )
                .setParameter("studyId", studyId)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);

        if (existing == null) {
            entityManager.persist(Schedule.create(
                    studyId,
                    startAt,
                    endAt,
                    properties.getMeetingPlace()
            ));
            log.info(
                    "[demoseed] 임장 일정 생성: {} — {} ({}시간, {})",
                    studyTitle,
                    properties.getFieldVisitAt(),
                    properties.getFieldVisitDurationHours(),
                    properties.getMeetingPlace()
            );
            return;
        }

        boolean alreadySeeded = startAt.equals(existing.getStartAt())
                && properties.getMeetingPlace().equals(existing.getMeetingPlace())
                && existing.getStatus() == ScheduleStatus.SCHEDULED;
        if (alreadySeeded) {
            return;
        }

        Instant previousStartAt = existing.getStartAt();
        // reactivate 는 status 도 SCHEDULED 로 되돌린다 — 리허설로 COMPLETED 가 된 일정도 살린다.
        existing.reactivate(startAt, endAt, properties.getMeetingPlace());
        log.info(
                "[demoseed] 임장 일정 갱신: {} — {} (이전 {})",
                studyTitle,
                properties.getFieldVisitAt(),
                previousStartAt
        );
    }

    private void seedStudyMembers(Long studyId, Long leaderId, List<Member> members) {
        for (Member member : members) {
            boolean exists = !entityManager
                    .createQuery("""
                            select sm from StudyMember sm
                            where sm.studyId = :studyId and sm.memberId = :memberId
                            """, StudyMember.class)
                    .setParameter("studyId", studyId)
                    .setParameter("memberId", member.getId())
                    .getResultList()
                    .isEmpty();
            if (exists) {
                continue;
            }

            StudyMember studyMember = member.getId().equals(leaderId)
                    ? StudyMember.createLeader(studyId, member.getId())
                    : StudyMember.createMember(studyId, member.getId());
            entityManager.persist(studyMember);
        }
    }

    // ---------------------------------------------------------------- 리포트

    /**
     * 리포트용 과거 임장 스터디. 시연용 5개와 <b>분리</b>해야 한다 —
     * {@code report.study_id}가 UNIQUE라 스터디 1개당 리포트가 1개뿐이고,
     * 리포트가 붙은 스터디는 COMPLETED가 되어 임장을 다시 시작할 수 없다.
     */
    private List<Long> seedReportStudies(Long apartmentId, Long leaderId, List<Member> members) {
        int count = Math.min(properties.getReportStudyCount(), DemoSeedCatalog.REPORTS.size());
        if (count < properties.getReportStudyCount()) {
            log.warn(
                    "[demoseed] 리포트 본문이 {}건뿐이라 reportStudyCount={} 요청을 {}건으로 줄였습니다.",
                    DemoSeedCatalog.REPORTS.size(),
                    properties.getReportStudyCount(),
                    count
            );
        }

        Instant baseTime = Instant.now().minus(Duration.ofDays(30));
        List<Long> reportIds = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            DemoSeedCatalog.ReportContent content = DemoSeedCatalog.REPORTS.get(index);
            String title = REPORT_STUDY_TITLE_FORMAT.formatted(index + 1);
            Instant visitedAt = baseTime.plus(Duration.ofDays(index * 3L));

            Study study = findStudyByTitle(title);
            if (study == null) {
                study = Study.create(
                        apartmentId,
                        leaderId,
                        title,
                        "지난 임장 기록입니다. 리포트를 확인해 보세요.",
                        content.title(),
                        DemoSeedCatalog.STUDY_CAPACITY,
                        DemoSeedCatalog.TEAM_STUDIES.get(index).purpose()
                );
                entityManager.persist(study);
                entityManager.flush();
                log.info("[demoseed] 리포트용 스터디 생성: {} (id={})", title, study.getId());
            }
            seedStudyMembers(study.getId(), leaderId, members);

            Report report = findReportByStudyId(study.getId());
            if (report != null) {
                log.info("[demoseed] 리포트 재사용: studyId={} reportId={}", study.getId(), report.getId());
                reportIds.add(report.getId());
                continue;
            }

            advanceStudyToCompleted(study, visitedAt);
            Long fieldSessionId = seedEndedFieldSession(study.getId(), leaderId, visitedAt);

            report = Report.create(study.getId(), fieldSessionId, apartmentId);
            entityManager.persist(report);

            JsonNode resultJson = objectMapper.valueToTree(
                    DemoSeedReportPayload.build(content, members.size())
            );
            report.complete(
                    resultJson,
                    sha256(resultJson.toString()),
                    visitedAt.plus(Duration.ofHours(2))
            );
            entityManager.flush();

            log.info("[demoseed] 리포트 생성: studyId={} reportId={}", study.getId(), report.getId());
            reportIds.add(report.getId());
        }
        return reportIds;
    }

    /** RECRUITING → CLOSED → IN_PROGRESS → COMPLETED. 상태 기계가 이 순서만 허용한다. */
    private void advanceStudyToCompleted(Study study, Instant visitedAt) {
        if (study.getStatus() == com.ssafy.ssabangpalbang.study.domain.StudyStatus.RECRUITING) {
            study.closeRecruitment(visitedAt);
        }
        if (study.getStatus() == com.ssafy.ssabangpalbang.study.domain.StudyStatus.CLOSED) {
            study.markInProgress();
        }
        study.markCompleted();
    }

    private Long seedEndedFieldSession(Long studyId, Long leaderId, Instant startedAt) {
        FieldSession session = FieldSession.start(studyId, startedAt);
        session.endByLeader(startedAt.plus(Duration.ofHours(2)), leaderId);
        entityManager.persist(session);
        entityManager.flush();
        return session.getId();
    }

    private Report findReportByStudyId(Long studyId) {
        return entityManager
                .createQuery("select r from Report r where r.studyId = :studyId", Report.class)
                .setParameter("studyId", studyId)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------- 커뮤니티

    private void seedPosts(Long apartmentId, List<Member> members) {
        for (DemoSeedCatalog.Post definition : DemoSeedCatalog.POSTS) {
            Long authorId = members.get(definition.authorIndex()).getId();
            boolean exists = !entityManager
                    .createQuery("""
                            select p from Post p
                            where p.authorId = :authorId and p.title = :title
                            """, Post.class)
                    .setParameter("authorId", authorId)
                    .setParameter("title", definition.title())
                    .getResultList()
                    .isEmpty();
            if (exists) {
                continue;
            }

            entityManager.persist(Post.createMemberPost(
                    authorId,
                    definition.boardType(),
                    definition.title(),
                    definition.content().strip(),
                    definition.linkApartment() ? apartmentId : null
            ));
            log.info("[demoseed] 게시글 생성: {}", definition.title());
        }
    }

    // ---------------------------------------------------------------- 공통

    private Study findStudyByTitle(String title) {
        return entityManager
                .createQuery("select s from Study s where s.title = :title", Study.class)
                .setParameter("title", title)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
