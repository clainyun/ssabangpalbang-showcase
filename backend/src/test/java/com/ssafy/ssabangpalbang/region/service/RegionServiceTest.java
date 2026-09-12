package com.ssafy.ssabangpalbang.region.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.region.repository.RegionDongQueryRepository;
import com.ssafy.ssabangpalbang.region.repository.RegionDongRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionServiceTest {

    private MemberRepository memberRepository;
    private RegionDongQueryRepository regionDongQueryRepository;
    private RegionService regionService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        regionDongQueryRepository = mock(RegionDongQueryRepository.class);
        regionService = new RegionService(memberRepository, regionDongQueryRepository);
        activeMember();
    }

    @Test
    void 자치구_목록은_서울_25개를_가나다순으로_반환한다() {
        var response = regionService.getDistricts(1L);

        assertThat(response.cityCode()).isEqualTo("11");
        assertThat(response.cityName()).isEqualTo("서울특별시");
        assertThat(response.totalCount()).isEqualTo(25);
        assertThat(response.districts()).hasSize(25);
        assertThat(response.districts().get(0).districtName()).isEqualTo("강남구");
        assertThat(response.districts().get(24).districtName()).isEqualTo("중랑구");
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("hasApartment", "apartmentCount");
    }

    @Test
    void 회원이_없거나_활성상태가_아니면_조회할_수_없다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        assertError(() -> regionService.getDistricts(1L), ErrorCode.MEMBER_NOT_FOUND);

        Member withdrawn = mock(Member.class);
        when(withdrawn.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(withdrawn));
        assertError(() -> regionService.getDistricts(1L), ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void 동_목록은_상수의_자치구명과_집계결과를_반환한다() {
        when(regionDongQueryRepository.findByDistrictCode("11710"))
                .thenReturn(List.of(new RegionDongRow("1171010100", "잠실동", 18L)));

        var response = regionService.getDongs(1L, "11710");

        assertThat(response.districtName()).isEqualTo("송파구");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.dongs().get(0).apartmentCount()).isEqualTo(18);
        assertThat(response.dongs().get(0).hasApartment()).isTrue();
    }

    @Test
    void 유효한_자치구의_동이_없으면_빈_목록을_반환한다() {
        when(regionDongQueryRepository.findByDistrictCode("11710"))
                .thenReturn(List.of());

        var response = regionService.getDongs(1L, " 11710 ");

        assertThat(response.districtCode()).isEqualTo("11710");
        assertThat(response.dongs()).isEmpty();
        assertThat(response.totalCount()).isZero();
    }

    @Test
    void 자치구_코드_오류를_정확히_구분한다() {
        assertError(() -> regionService.getDongs(1L, "1171"),
                ErrorCode.REGION_DISTRICT_CODE_INVALID);
        assertError(() -> regionService.getDongs(1L, " 11a80 "),
                ErrorCode.REGION_DISTRICT_CODE_INVALID);
        assertErrorData(() -> regionService.getDongs(1L, "26110"),
                ErrorCode.REGION_OUT_OF_SERVICE_AREA, "26110");
        assertErrorData(() -> regionService.getDongs(1L, "99999"),
                ErrorCode.REGION_DISTRICT_NOT_FOUND, "99999");
        assertError(() -> regionService.getDongs(1L, "11999"),
                ErrorCode.REGION_DISTRICT_NOT_FOUND);
    }

    @Test
    void 회원이_없으면_동_목록을_조회할_수_없다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        assertError(() -> regionService.getDongs(1L, "11710"),
                ErrorCode.MEMBER_NOT_FOUND);
    }

    private void activeMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private void assertErrorData(
            Runnable action,
            ErrorCode errorCode,
            String districtCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(errorCode);
                    assertThat(exception.getData())
                            .containsEntry("districtCode", districtCode);
                });
    }
}
