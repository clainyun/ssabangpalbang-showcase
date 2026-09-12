package com.ssafy.ssabangpalbang.apartment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentDistrictSummaryQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentDistrictSummaryRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApartmentDistrictSummaryServiceTest {

    private ApartmentDistrictSummaryQueryRepository summaryRepository;
    private MemberRepository memberRepository;
    private ApartmentService service;

    @BeforeEach
    void setUp() {
        summaryRepository = mock(ApartmentDistrictSummaryQueryRepository.class);
        memberRepository = mock(MemberRepository.class);
        service = new ApartmentService(
                mock(ApartmentRepository.class),
                mock(ApartmentTransactionRepository.class)
        );
        ReflectionTestUtils.setField(
                service,
                "apartmentDistrictSummaryQueryRepository",
                summaryRepository
        );
        ReflectionTestUtils.setField(service, "memberRepository", memberRepository);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember()));
    }

    @Test
    void 집계_결과가_있는_구는_개수와_중심좌표를_반환한다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of(
                row("11680", 352, 37.4979, 127.0276)
        ));

        var response = service.getDistrictSummary(1L);
        var gangnam = response.districts().stream()
                .filter(item -> item.districtCode().equals("11680"))
                .findFirst()
                .orElseThrow();

        assertThat(gangnam.districtName()).isEqualTo("강남구");
        assertThat(gangnam.apartmentCount()).isEqualTo(352);
        assertThat(gangnam.centerLatitude()).isEqualTo(37.4979);
        assertThat(gangnam.centerLongitude()).isEqualTo(127.0276);
    }

    @Test
    void 집계_결과가_없는_구도_0건으로_포함된다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of());

        var response = service.getDistrictSummary(1L);

        assertThat(response.districts()).hasSize(25).allSatisfy(item -> {
            assertThat(item.apartmentCount()).isZero();
            assertThat(item.centerLatitude()).isNull();
            assertThat(item.centerLongitude()).isNull();
        });
    }

    @Test
    void districts는_항상_25건이다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of(
                row("11680", 1, 37.5, 127.0),
                row("11740", 2, 37.6, 127.1),
                row("11545", 3, 37.4, 126.9)
        ));

        assertThat(service.getDistrictSummary(1L).districts()).hasSize(25);
    }

    @Test
    void 자치구는_이름_가나다순으로_정렬된다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of());

        var districts = service.getDistrictSummary(1L).districts();

        assertThat(districts.get(0).districtName()).isEqualTo("강남구");
        assertThat(districts.get(24).districtName()).isEqualTo("중랑구");
    }

    @Test
    void totalApartmentCount는_구별_개수의_합이다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of(
                row("11680", 352, 37.5, 127.0),
                row("11740", 241, 37.6, 127.1)
        ));

        assertThat(service.getDistrictSummary(1L).totalApartmentCount()).isEqualTo(593);
    }

    @Test
    void totalCount는_25다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of());

        assertThat(service.getDistrictSummary(1L).totalCount()).isEqualTo(25);
    }

    @Test
    void SeoulDistrict에_없는_district_code는_버린다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of(
                row("11680", 10, 37.5, 127.0),
                row("41135", 999, 37.4, 127.1)
        ));

        var response = service.getDistrictSummary(1L);

        assertThat(response.districts())
                .noneMatch(item -> item.districtCode().equals("41135"));
        assertThat(response.totalApartmentCount()).isEqualTo(10);
    }

    @Test
    void 회원이_없으면_MEMBER_NOT_FOUND() {
        when(memberRepository.findById(2L)).thenReturn(Optional.empty());

        assertMemberNotFound(2L);
    }

    @Test
    void 탈퇴한_회원이면_MEMBER_NOT_FOUND() {
        Member deleted = activeMember();
        ReflectionTestUtils.setField(
                deleted,
                "deletedAt",
                Instant.parse("2026-08-06T00:00:00Z")
        );
        when(memberRepository.findById(3L)).thenReturn(Optional.of(deleted));

        assertMemberNotFound(3L);
    }

    @Test
    void 비활성_회원이면_MEMBER_NOT_FOUND() {
        Member inactive = activeMember();
        ReflectionTestUtils.setField(inactive, "status", MemberStatus.WITHDRAWN);
        when(memberRepository.findById(4L)).thenReturn(Optional.of(inactive));

        assertMemberNotFound(4L);
    }

    @Test
    void 중심좌표가_null인_구는_직렬화에서_null로_나간다() {
        when(summaryRepository.findDistrictSummaries()).thenReturn(List.of());

        var json = new ObjectMapper().valueToTree(service.getDistrictSummary(1L));

        assertThat(json.get("districts").get(0).get("centerLatitude").isNull()).isTrue();
        assertThat(json.get("districts").get(0).get("centerLongitude").isNull()).isTrue();
    }

    private void assertMemberNotFound(Long memberId) {
        assertThatThrownBy(() -> service.getDistrictSummary(memberId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );
    }

    private Member activeMember() {
        return new Member("member@test.com", "hash", "member");
    }

    private ApartmentDistrictSummaryRow row(
            String districtCode,
            long apartmentCount,
            Double centerLatitude,
            Double centerLongitude
    ) {
        return new ApartmentDistrictSummaryRow(
                districtCode,
                apartmentCount,
                centerLatitude,
                centerLongitude
        );
    }
}
