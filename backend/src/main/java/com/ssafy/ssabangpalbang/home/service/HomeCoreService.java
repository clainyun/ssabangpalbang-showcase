package com.ssafy.ssabangpalbang.home.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.home.dto.response.HomeResponse;
import com.ssafy.ssabangpalbang.home.repository.HomeNextVisitRow;
import com.ssafy.ssabangpalbang.home.repository.HomeQueryRepository;
import com.ssafy.ssabangpalbang.home.weather.HomeWeatherLocation;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
class HomeCoreService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final HomeQueryRepository homeQueryRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HomeCoreData load(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .filter(value -> value.getStatus() == MemberStatus.ACTIVE)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        Instant now = clock.instant();
        LocalDate today = now.atZone(SEOUL_ZONE_ID).toLocalDate();
        // 요구사항: 지난 날짜의 예정 임장은 종료 여부와 무관하게 제외하되, 당일 임장은
        // 시작 시간이 지나도 종료 전까지 계속 노출한다. 따라서 시각(now)이 아니라
        // 오늘 0시(Asia/Seoul) 이후로 시작하는 일정만 대상으로 삼는다.
        Instant startOfToday = today.atStartOfDay(SEOUL_ZONE_ID).toInstant();
        List<HomeNextVisitRow> nextVisitRows = homeQueryRepository
                .findNextVisit(
                        memberId,
                        startOfToday,
                        PageRequest.of(0, 1)
                );
        HomeNextVisitRow nextVisitRow = nextVisitRows.isEmpty()
                ? null
                : nextVisitRows.get(0);

        return new HomeCoreData(
                today,
                notificationRepository
                        .countByRecipientIdAndIsReadFalse(memberId),
                toNextVisit(nextVisitRow, today),
                toWeatherLocation(nextVisitRow)
        );
    }

    private HomeResponse.NextVisit toNextVisit(
            HomeNextVisitRow row,
            LocalDate today
    ) {
        if (row == null) {
            return HomeResponse.NextVisit.empty();
        }

        LocalDate visitDate = row.getStartAt()
                .atZone(SEOUL_ZONE_ID)
                .toLocalDate();
        return new HomeResponse.NextVisit(
                true,
                Math.max(0L, ChronoUnit.DAYS.between(today, visitDate)),
                row.getStudyId(),
                row.getApartmentName(),
                row.getMeetingPlace(),
                row.getStartAt().atZone(SEOUL_ZONE_ID).toOffsetDateTime(),
                row.getCurrentMemberCount(),
                row.getCapacity()
        );
    }

    private HomeWeatherLocation toWeatherLocation(HomeNextVisitRow row) {
        if (row == null) {
            return null;
        }
        String name = row.getDistrictName();
        if (name == null || name.isBlank()) {
            name = row.getApartmentAddress();
        }
        if (name == null || name.isBlank()) {
            name = row.getApartmentName();
        }
        return new HomeWeatherLocation(
                row.getLatitude(),
                row.getLongitude(),
                name,
                HomeWeatherLocation.Basis.NEXT_VISIT_APARTMENT
        );
    }

}
