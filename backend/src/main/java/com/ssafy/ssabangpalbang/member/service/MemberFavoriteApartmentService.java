package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentFavorite;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentLatestTransaction;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentCountRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentFavoriteRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentImageKeyRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentReportQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.apartment.service.ApartmentImageUrlResolver;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberFavoriteApartmentResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberFavoriteApartmentService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final ApartmentFavoriteRepository apartmentFavoriteRepository;
    private final ApartmentRepository apartmentRepository;
    private final ApartmentTransactionRepository apartmentTransactionRepository;
    private final StudyRepository studyRepository;
    private final ApartmentReportQueryRepository apartmentReportQueryRepository;
    private final ApartmentImageUrlResolver apartmentImageUrlResolver;

    @Transactional(readOnly = true)
    public PageResponse<MemberFavoriteApartmentResponse> getFavoriteApartments(
            Long memberId,
            int page,
            int size
    ) {
        validateActiveMember(memberId);

        Page<ApartmentFavorite> favorites =
                apartmentFavoriteRepository
                        .findByMemberIdOrderByCreatedAtDescIdDesc(
                                memberId,
                                PageRequest.of(page, size)
                        );
        if (favorites.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    favorites.getTotalElements(),
                    favorites.getNumber(),
                    favorites.getSize(),
                    favorites.getTotalPages()
            );
        }

        List<Long> apartmentIds = favorites.getContent().stream()
                .map(ApartmentFavorite::getApartmentId)
                .toList();
        Map<Long, Apartment> apartments = apartmentRepository
                .findAllById(apartmentIds).stream()
                .collect(Collectors.toMap(
                        Apartment::getId,
                        Function.identity()
                ));
        Map<Long, ApartmentLatestTransaction> latestTransactions =
                apartmentTransactionRepository
                        .findLatestNormalByApartmentIds(apartmentIds).stream()
                        .collect(Collectors.toMap(
                                ApartmentTransaction::getApartmentId,
                                ApartmentLatestTransaction::from
                        ));
        Map<Long, Long> recruitingStudyCounts = studyRepository
                .countRecruitingByApartmentIds(apartmentIds).stream()
                .collect(Collectors.toMap(
                        StudyRepository.ApartmentStudyCountRow::getApartmentId,
                        StudyRepository.ApartmentStudyCountRow::getCount
                ));
        Map<Long, Long> completedReportCounts =
                apartmentReportQueryRepository
                        .countByApartmentIds(apartmentIds).stream()
                        .collect(Collectors.toMap(
                                ApartmentCountRow::apartmentId,
                                ApartmentCountRow::count
                        ));
        // 대표 이미지 URL을 한 번에 배치 조회(N+1 방지). resolve가 null이면(미설정·미매칭)
        // 맵에 담지 않아 응답 imageUrl이 null이 되고, 프론트가 로컬 일러스트로 폴백한다.
        Map<Long, String> imageUrls = new HashMap<>();
        for (ApartmentImageKeyRow row : apartmentRepository
                .findImageObjectKeysByApartmentIds(apartmentIds)) {
            String url = apartmentImageUrlResolver.resolve(row.getObjectKey());
            if (url != null) {
                imageUrls.put(row.getApartmentId(), url);
            }
        }

        return PageResponse.from(favorites.map(favorite -> toResponse(
                favorite,
                requireApartment(apartments, favorite.getApartmentId()),
                latestTransactions.get(favorite.getApartmentId()),
                recruitingStudyCounts.getOrDefault(
                        favorite.getApartmentId(),
                        0L
                ),
                completedReportCounts.getOrDefault(
                        favorite.getApartmentId(),
                        0L
                ),
                imageUrls.get(favorite.getApartmentId())
        )));
    }

    private void validateActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Apartment requireApartment(
            Map<Long, Apartment> apartments,
            Long apartmentId
    ) {
        Apartment apartment = apartments.get(apartmentId);
        if (apartment == null) {
            throw new BusinessException(ErrorCode.APARTMENT_NOT_FOUND);
        }
        return apartment;
    }

    private MemberFavoriteApartmentResponse toResponse(
            ApartmentFavorite favorite,
            Apartment apartment,
            ApartmentLatestTransaction latestTransaction,
            long recruitingStudyCount,
            long completedReportCount,
            String imageUrl
    ) {
        return new MemberFavoriteApartmentResponse(
                apartment.getId(),
                apartment.getName(),
                apartment.getAddress(),
                apartment.getDistrictName(),
                apartment.getDongName(),
                apartment.getHouseholdCount(),
                latestTransaction,
                Math.toIntExact(recruitingStudyCount),
                Math.toIntExact(completedReportCount),
                OffsetDateTime.ofInstant(favorite.getCreatedAt(), SEOUL),
                imageUrl
        );
    }
}
