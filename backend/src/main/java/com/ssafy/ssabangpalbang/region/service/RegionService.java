package com.ssafy.ssabangpalbang.region.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.region.domain.SeoulDistrict;
import com.ssafy.ssabangpalbang.region.dto.response.DistrictListResponse;
import com.ssafy.ssabangpalbang.region.dto.response.DongListResponse;
import com.ssafy.ssabangpalbang.region.repository.RegionDongQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegionService {

    private static final Set<String> KNOWN_SIDO_PREFIXES = Set.of(
            "11", "26", "27", "28", "29", "30", "31", "36",
            "41", "42", "43", "44", "45", "46", "47", "48",
            "50", "51", "52"
    );

    private final MemberRepository memberRepository;
    private final RegionDongQueryRepository regionDongQueryRepository;

    @Transactional(readOnly = true)
    public DistrictListResponse getDistricts(Long memberId) {
        validateMember(memberId);
        return DistrictListResponse.from(SeoulDistrict.values());
    }

    @Transactional(readOnly = true)
    public DongListResponse getDongs(Long memberId, String districtCode) {
        validateMember(memberId);
        String normalizedCode = validateDistrictCode(districtCode);
        SeoulDistrict district = SeoulDistrict.findByCode(normalizedCode)
                .orElseThrow(() -> districtCodeException(normalizedCode));
        return DongListResponse.from(
                district,
                regionDongQueryRepository.findByDistrictCode(normalizedCode)
        );
    }

    private void validateMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private String validateDistrictCode(String districtCode) {
        String normalizedCode = districtCode == null ? "" : districtCode.trim();
        if (!normalizedCode.matches("\\d{5}")) {
            throw new BusinessException(
                    ErrorCode.REGION_DISTRICT_CODE_INVALID,
                    Map.of(
                            "field", "districtCode",
                            "reason", "자치구 코드는 5자리 숫자여야 합니다."
                    )
            );
        }
        return normalizedCode;
    }

    private BusinessException districtCodeException(String districtCode) {
        String sidoPrefix = districtCode.substring(0, 2);
        if (!SeoulDistrict.CITY_CODE.equals(sidoPrefix)
                && KNOWN_SIDO_PREFIXES.contains(sidoPrefix)) {
            return new BusinessException(
                    ErrorCode.REGION_OUT_OF_SERVICE_AREA,
                    Map.of("districtCode", districtCode)
            );
        }
        return new BusinessException(
                ErrorCode.REGION_DISTRICT_NOT_FOUND,
                Map.of("districtCode", districtCode)
        );
    }
}
