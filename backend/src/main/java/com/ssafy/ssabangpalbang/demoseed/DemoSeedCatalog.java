package com.ssafy.ssabangpalbang.demoseed;

import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;

import java.util.List;

/**
 * 시연 데이터 정본. 값은 {@code _workspace/demo-apk/CHECKLIST.md} §4 표와 한 칸도 달라선 안 된다.
 *
 * <p>여기 있는 값만 바꾸면 시더 전체가 따라간다. 코드 흐름에는 값을 흩어놓지 않는다.</p>
 */
final class DemoSeedCatalog {

    /** 시연 팀 수 = 스터디 수 = 계정 수. */
    static final int TEAM_COUNT = 5;

    /** 스터디 정원. 계정 5개가 모두 멤버로 들어가므로 5다. */
    static final int STUDY_CAPACITY = 5;

    /** 계정 5개. index 0 이 usertest1 이고 5개 스터디 전부의 스터디장이다. */
    static final List<Account> ACCOUNTS = List.of(
            new Account(
                    "usertest1@test.com", "도곡탐방러",
                    "THIRTIES", true, "MARRIED", true, true,
                    "RESIDENCE", List.of("EDUCATION", "SAFETY", "PARKING", "COMMERCIAL"),
                    "PALBANG"
            ),
            new Account(
                    "usertest2@test.com", "투자연습생",
                    "TWENTIES", false, "SINGLE", false, false,
                    "INVESTMENT", List.of("TRANSPORT", "COMMERCIAL", "WALKABILITY"),
                    "PALBANG_RABBIT"
            ),
            new Account(
                    "usertest3@test.com", "임장공부중",
                    "FORTIES", true, "MARRIED", true, true,
                    "STUDY", List.of("EDUCATION", "GREEN_SPACE", "NOISE", "SAFETY"),
                    "PALBANG_DOG"
            ),
            new Account(
                    "usertest4@test.com", "조용한동네선호",
                    "FIFTIES", true, "MARRIED", false, true,
                    "RESIDENCE", List.of("GREEN_SPACE", "WALKABILITY", "NOISE", "PARKING"),
                    "PALBANG"
            ),
            new Account(
                    "usertest5@test.com", "첫임장",
                    "TWENTIES", false, "SINGLE", false, false,
                    "INVESTMENT", List.of("TRANSPORT", "COMMERCIAL"),
                    "PALBANG_RABBIT"
            )
    );

    /**
     * 시연용 스터디 5개. 목적 3종이 모두 한 번 이상 나오도록 배분했다.
     *
     * <p>상태는 반드시 CLOSED(모집 마감)여야 한다 — {@code Study.markInProgress()}가
     * CLOSED가 아니면 예외를 던지므로 RECRUITING 상태에서는 임장을 시작할 수 없다.</p>
     */
    static final List<TeamStudy> TEAM_STUDIES = List.of(
            new TeamStudy(1, "도곡렉슬 임장 1팀", StudyPurpose.RESIDENCE,
                    "실거주 관점으로 도곡렉슬을 함께 봅니다.",
                    "학군·단지 관리 상태·주차 여건을 직접 확인한다"),
            new TeamStudy(2, "도곡렉슬 임장 2팀", StudyPurpose.INVESTMENT,
                    "투자 관점으로 도곡렉슬을 함께 봅니다.",
                    "역세권 접근성과 상권 활성도를 확인한다"),
            new TeamStudy(3, "도곡렉슬 임장 3팀", StudyPurpose.STUDY,
                    "임장 연습 목적으로 도곡렉슬을 함께 봅니다.",
                    "체크리스트 작성과 기록 남기기를 익힌다"),
            new TeamStudy(4, "도곡렉슬 임장 4팀", StudyPurpose.RESIDENCE,
                    "조용한 주거 환경을 중심으로 살펴봅니다.",
                    "소음·녹지·보행 환경을 확인한다"),
            new TeamStudy(5, "도곡렉슬 임장 5팀", StudyPurpose.INVESTMENT,
                    "가격과 거래 흐름을 중심으로 살펴봅니다.",
                    "최근 실거래 흐름과 매물 상태를 확인한다")
    );

    /** 커뮤니티 게시글. authorIndex 는 ACCOUNTS 의 index. */
    static final List<Post> POSTS = List.of(
            new Post(0, BoardType.INFORMATION,
                    "도곡렉슬 임장 전에 확인하면 좋은 것들",
                    """
                    선릉로 정문 기준으로 지하철역까지 도보 시간을 꼭 재보세요.
                    단지가 3,002세대라 동 위치에 따라 체감이 많이 다릅니다.
                    주차는 세대당 1대 이상 확보되는 편이지만 방문 주차는 별도로 확인이 필요합니다.
                    """, true),
            new Post(1, BoardType.INFORMATION,
                    "강남구 도곡동 최근 실거래 흐름 정리",
                    """
                    최근 거래는 전용 84㎡ 위주로 이어지고 있습니다.
                    같은 평형도 층·향에 따라 차이가 커서 단순 평균으로 보면 오해가 생깁니다.
                    임장 때 관리사무소에서 동별 특성을 물어보면 판단이 훨씬 쉬워집니다.
                    """, true),
            new Post(2, BoardType.FREE,
                    "임장 처음 가는데 뭘 준비해야 하나요?",
                    """
                    편한 신발과 보조배터리면 충분합니다.
                    체크리스트는 앱이 만들어주니까 현장에서는 사진과 음성 메모만 부지런히 남기세요.
                    저는 첫 임장에서 사진을 너무 적게 찍어서 나중에 기억이 안 났습니다.
                    """, false),
            new Post(3, BoardType.FREE,
                    "단지 안 산책로가 생각보다 좋네요",
                    """
                    조경이 잘 관리돼 있어서 단지 안에서만 걸어도 답답하지 않았습니다.
                    아이들 놀이터도 여러 곳에 나뉘어 있어 붐비지 않는 편이었습니다.
                    """, true),
            new Post(4, BoardType.FREE,
                    "임장 다녀온 후에 리포트 보는 재미가 있어요",
                    """
                    팀원들이 각자 남긴 기록이 한 번에 정리돼서 나오는 게 좋았습니다.
                    저는 교통을 봤는데 다른 분은 소음을 보셔서 관점이 달라 도움이 됐습니다.
                    """, false)
    );

    /** 과거 임장 스터디에 붙일 리포트 본문. reportStudyCount 만큼 앞에서부터 쓴다. */
    static final List<ReportContent> REPORTS = List.of(
            new ReportContent(
                    "도곡렉슬 임장 리포트 — 실거주 관점",
                    """
                    도곡렉슬은 3,002세대 대단지로 단지 내 보행 동선과 조경 관리가 안정적입니다.
                    학군과 생활 편의는 참가자 전원이 긍정적으로 평가했고, 주차는 세대당 확보 대수는
                    넉넉하지만 방문 주차 여건에서는 의견이 갈렸습니다. 선릉로 정문 기준 지하철 접근은
                    도보 10분 내외로, 동 위치에 따라 체감 차이가 큽니다.
                    """,
                    "교통", "지하철 접근성", "학군", "주차 여건"
            ),
            new ReportContent(
                    "도곡렉슬 임장 리포트 — 투자 관점",
                    """
                    거래는 전용 84㎡ 중심으로 이어지고 있으며, 단지 규모 덕분에 매물 회전이 비교적
                    꾸준합니다. 상권은 선릉로 방향이 활발하고 남쪽은 조용한 편이라 목적에 따라 선호가
                    갈립니다. 준공 2006년으로 설비 노후도는 참가자 간 평가가 엇갈렸습니다.
                    """,
                    "상권·생활편의", "상권 활성도", "가격 흐름", "설비 노후도"
            ),
            new ReportContent(
                    "도곡렉슬 임장 리포트 — 주거 환경 관점",
                    """
                    단지 내부는 차량 동선과 보행 동선이 분리돼 있어 아이를 데리고 걷기에 무리가 없었고,
                    놀이터가 여러 곳에 분산돼 혼잡하지 않았습니다. 대로변 동은 소음이 체감되며 안쪽 동은
                    조용해, 동 선택이 만족도를 크게 좌우한다는 의견이 공통적으로 나왔습니다.
                    """,
                    "단지환경", "보행 동선", "녹지", "대로변 소음"
            )
    );

    private DemoSeedCatalog() {
    }

    record Account(
            String email,
            String nickname,
            String ageGroup,
            boolean ageGroupPublicAgreed,
            String maritalStatus,
            boolean hasChildren,
            boolean hasVehicle,
            String purpose,
            List<String> priorities,
            String characterId
    ) {
    }

    record TeamStudy(
            int teamNumber,
            String title,
            StudyPurpose purpose,
            String intro,
            String goal
    ) {
    }

    record Post(
            int authorIndex,
            BoardType boardType,
            String title,
            String content,
            boolean linkApartment
    ) {
    }

    /**
     * @param positiveLabel 긍정 상위 항목 라벨
     * @param cautionLabel  주의 상위 항목 라벨
     */
    record ReportContent(
            String title,
            String summary,
            String category,
            String positiveLabel,
            String commonLabel,
            String cautionLabel
    ) {
    }
}
