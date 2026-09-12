package com.ssafy.ssabangpalbang.home.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.home.repository.HomeNextVisitRow;
import com.ssafy.ssabangpalbang.home.repository.HomeQueryRepository;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeCoreServiceTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    // HomeCoreService는 now가 아니라 오늘 0시(Asia/Seoul)를 조회 기준으로 넘긴다.
    // NOW(2026-07-24T00:00Z = KST 09:00)의 당일 자정 KST = 2026-07-23T15:00Z.
    private static final Instant START_OF_TODAY = Instant.parse(
            "2026-07-23T15:00:00Z"
    );

    private final MemberRepository memberRepository = mock(
            MemberRepository.class
    );
    private final HomeQueryRepository homeQueryRepository = mock(
            HomeQueryRepository.class
    );
    private final NotificationRepository notificationRepository = mock(
            NotificationRepository.class
    );
    private final HomeCoreService service = new HomeCoreService(
            memberRepository,
            homeQueryRepository,
            notificationRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void 홈_핵심_데이터를_정렬된_집계_결과로_변환한다() {
        mockActiveMember();

        HomeNextVisitRow nextVisit = nextVisit();
        when(homeQueryRepository.findNextVisit(
                eq(1L),
                eq(START_OF_TODAY),
                any(Pageable.class)
        )).thenReturn(List.of(nextVisit));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(1L))
                .thenReturn(2L);

        HomeCoreData result = service.load(1L);

        assertThat(result.today().toString()).isEqualTo("2026-07-24");
        assertThat(result.nextVisit().exists()).isTrue();
        assertThat(result.nextVisit().dDay()).isEqualTo(3L);
        assertThat(result.nextVisit().studyId()).isEqualTo(10L);
        assertThat(result.nextVisit().apartmentName())
                .isEqualTo("옥수 아파트");
        assertThat(result.nextVisit().meetingPlace())
                .isEqualTo("옥수역 3번 출구");
        assertThat(result.nextVisit().startAt().toString())
                .isEqualTo("2026-07-27T15:00+09:00");
        assertThat(result.unreadNotificationCount()).isEqualTo(2L);
        assertThat(result.nextVisitWeatherLocation().name())
                .isEqualTo("성동구");
    }

    @Test
    void 당일_임장은_시작_시간이_지나도_D_day_0으로_노출한다() {
        mockActiveMember();

        HomeNextVisitRow nextVisit = nextVisit();
        // 오늘(2026-07-24 KST)이지만 현재 시각(KST 09:00)보다 이른 08:00 시작 일정.
        when(nextVisit.getStartAt())
                .thenReturn(Instant.parse("2026-07-23T23:00:00Z"));
        when(homeQueryRepository.findNextVisit(
                eq(1L),
                eq(START_OF_TODAY),
                any(Pageable.class)
        )).thenReturn(List.of(nextVisit));

        HomeCoreData result = service.load(1L);

        assertThat(result.nextVisit().exists()).isTrue();
        assertThat(result.nextVisit().dDay()).isZero();
    }

    @Test
    void 활성_회원이_아니면_회원_없음으로_처리한다() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.load(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    private void mockActiveMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
    }

    private HomeNextVisitRow nextVisit() {
        HomeNextVisitRow row = mock(HomeNextVisitRow.class);
        when(row.getStartAt())
                .thenReturn(Instant.parse("2026-07-27T06:00:00Z"));
        when(row.getMeetingPlace()).thenReturn("옥수역 3번 출구");
        when(row.getStudyId()).thenReturn(10L);
        when(row.getCapacity()).thenReturn(6);
        when(row.getCurrentMemberCount()).thenReturn(5L);
        when(row.getApartmentName()).thenReturn("옥수 아파트");
        when(row.getApartmentAddress()).thenReturn("서울 성동구");
        when(row.getDistrictName()).thenReturn("성동구");
        when(row.getLatitude()).thenReturn(37.54);
        when(row.getLongitude()).thenReturn(127.01);
        return row;
    }
}
