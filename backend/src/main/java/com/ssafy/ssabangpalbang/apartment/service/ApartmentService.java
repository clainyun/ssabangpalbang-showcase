package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentFavorite;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentSearchMode;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentReportSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentTransactionSearchCondition;
import com.ssafy.ssabangpalbang.apartment.dto.request.ApartmentBoundsCondition;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentBoundsItem;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentBoundsResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDetailResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDistrictSummaryItem;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentDistrictSummaryResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentFavoriteResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentFavoriteResult;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentResponseCode;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentTransactionResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentSearchItem;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentSearchLocation;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentSearchResponse;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentLatestTransaction;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentReportItem;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentBoundsQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentCountRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentDistrictSummaryQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentDistrictSummaryRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentFavoriteRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentSearchQueryRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentSearchRow;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentReportQueryRepository;
import com.ssafy.ssabangpalbang.apartment.support.ReportResultJsonParser;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.region.domain.SeoulDistrict;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApartmentService {

    private final ApartmentRepository apartmentRepository;
    private final ApartmentTransactionRepository apartmentTransactionRepository;
    private final ApartmentFavoriteRepository apartmentFavoriteRepository;
    private final MemberRepository memberRepository;
    private final PlatformTransactionManager transactionManager;
    @Autowired(required = false)
    private ApartmentSearchQueryRepository apartmentSearchQueryRepository;
    @Autowired(required = false)
    private ApartmentBoundsQueryRepository apartmentBoundsQueryRepository;
    @Autowired(required = false)
    private ApartmentDistrictSummaryQueryRepository apartmentDistrictSummaryQueryRepository;
    @Autowired(required = false)
    private ApartmentReportQueryRepository apartmentReportQueryRepository;
    @Autowired(required = false)
    private ReportResultJsonParser reportResultJsonParser;
    @Autowired(required = false)
    private StudyRepository studyRepository;
    @Autowired(required = false)
    private ApartmentImageUrlResolver apartmentImageUrlResolver;

    @Autowired
    public ApartmentService(
            ApartmentRepository apartmentRepository,
            ApartmentTransactionRepository apartmentTransactionRepository,
            ApartmentFavoriteRepository apartmentFavoriteRepository,
            MemberRepository memberRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.apartmentRepository = apartmentRepository;
        this.apartmentTransactionRepository = apartmentTransactionRepository;
        this.apartmentFavoriteRepository = apartmentFavoriteRepository;
        this.memberRepository = memberRepository;
        this.transactionManager = transactionManager;
    }

    public ApartmentService(
            ApartmentRepository apartmentRepository,
            ApartmentTransactionRepository apartmentTransactionRepository
    ) {
        this(
                apartmentRepository,
                apartmentTransactionRepository,
                null,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public ApartmentSearchResponse search(Long memberId, ApartmentSearchCondition condition) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (condition.districtCode() != null
                && !apartmentRepository.existsByDistrictCode(condition.districtCode())) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
        if (condition.dongCode() != null
                && !apartmentRepository.existsByLegalDongCode(condition.dongCode())) {
            throw new BusinessException(ErrorCode.REGION_NOT_FOUND);
        }
        Page<ApartmentSearchRow> page = apartmentSearchQueryRepository.search(condition);
        List<Long> ids = page.getContent().stream()
                .map(ApartmentSearchRow::apartmentId).toList();
        Map<Long, ApartmentTransaction> latest = ids.isEmpty() ? Map.of()
                : apartmentTransactionRepository.findLatestNormalByApartmentIds(ids).stream()
                .collect(Collectors.toMap(ApartmentTransaction::getApartmentId, Function.identity()));
        Set<Long> favorites = ids.isEmpty() ? Set.of()
                : Set.copyOf(apartmentFavoriteRepository.findFavoritedApartmentIds(memberId, ids));
        List<ApartmentSearchItem> content = page.getContent().stream()
                .map(row -> ApartmentSearchItem.from(
                        row, latest.get(row.apartmentId()), favorites.contains(row.apartmentId())))
                .toList();
        ApartmentSearchLocation location = condition.mode() == ApartmentSearchMode.NEARBY
                ? new ApartmentSearchLocation(
                        condition.latitude(), condition.longitude(), condition.radiusMeters(),
                        locationName(page.getContent()))
                : null;
        return new ApartmentSearchResponse(
                condition.mode(), location, content, page.getTotalElements(),
                condition.page(), condition.size(), page.getTotalPages()
        );
    }

    private String locationName(List<ApartmentSearchRow> rows) {
        if (rows.isEmpty()) return null;
        ApartmentSearchRow nearest = rows.get(0);
        if (nearest.districtName() == null || nearest.dongName() == null) return null;
        return "서울특별시 " + nearest.districtName() + " " + nearest.dongName();
    }

    @Transactional(readOnly = true)
    public PageResponse<ApartmentReportItem> getReports(
            Long memberId,
            ApartmentReportSearchCondition condition
    ) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!apartmentRepository.existsById(condition.apartmentId())) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_NOT_FOUND,
                    Map.of("apartmentId", condition.apartmentId())
            );
        }
        Page<ApartmentReportItem> reports = apartmentReportQueryRepository
                .findPublicReports(
                        condition.apartmentId(),
                        memberId,
                        condition.page(),
                        condition.size()
                )
                .map(row -> ApartmentReportItem.from(
                        row,
                        reportResultJsonParser.parse(
                                row.reportId(),
                                row.resultJson()
                        )
                ));
        return PageResponse.from(reports);
    }

    @Transactional(readOnly = true)
    public ApartmentBoundsResponse getBounds(
            Long memberId,
            ApartmentBoundsCondition condition
    ) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        var apartments = apartmentBoundsQueryRepository.findApartments(condition);
        if (apartments.isEmpty()) {
            return ApartmentBoundsResponse.of(List.of());
        }

        List<Long> apartmentIds = apartments.stream()
                .map(row -> row.apartmentId())
                .toList();
        Map<Long, Long> recruitingStudyCounts = studyRepository
                .countRecruitingByApartmentIds(apartmentIds).stream()
                .collect(Collectors.toMap(
                        StudyRepository.ApartmentStudyCountRow::getApartmentId,
                        StudyRepository.ApartmentStudyCountRow::getCount
                ));
        Map<Long, Long> completedReportCounts = toCountMap(
                apartmentReportQueryRepository.countByApartmentIds(apartmentIds)
        );
        Set<Long> favorites = Set.copyOf(
                apartmentFavoriteRepository.findFavoritedApartmentIds(memberId, apartmentIds)
        );

        List<ApartmentBoundsItem> items = apartments.stream()
                .map(row -> ApartmentBoundsItem.of(
                        row,
                        row.price() == null
                                ? null
                                : ApartmentLatestTransaction.of(
                                        row.price(), row.exclusiveArea(), row.dealDate()
                                ),
                        Math.toIntExact(recruitingStudyCounts.getOrDefault(row.apartmentId(), 0L)),
                        Math.toIntExact(completedReportCounts.getOrDefault(row.apartmentId(), 0L)),
                        favorites.contains(row.apartmentId())
                ))
                .toList();
        return ApartmentBoundsResponse.of(items);
    }

    @Transactional(readOnly = true)
    public ApartmentDistrictSummaryResponse getDistrictSummary(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Map<String, ApartmentDistrictSummaryRow> rowsByDistrictCode =
                apartmentDistrictSummaryQueryRepository.findDistrictSummaries().stream()
                        .collect(Collectors.toMap(
                                ApartmentDistrictSummaryRow::districtCode,
                                Function.identity()
                        ));

        List<ApartmentDistrictSummaryItem> districts = Arrays.stream(SeoulDistrict.values())
                .map(district -> ApartmentDistrictSummaryItem.of(
                        district,
                        rowsByDistrictCode.get(district.getCode())
                ))
                .sorted(Comparator.comparing(ApartmentDistrictSummaryItem::districtName))
                .toList();
        return ApartmentDistrictSummaryResponse.of(districts);
    }

    private Map<Long, Long> toCountMap(List<ApartmentCountRow> rows) {
        return rows.stream().collect(Collectors.toMap(
                ApartmentCountRow::apartmentId,
                ApartmentCountRow::count
        ));
    }

    @Transactional(readOnly = true)
    public ApartmentDetailResponse getDetail(Long memberId, Long apartmentId) {
        if (apartmentId == null || apartmentId < 1) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_ID_INVALID,
                    Map.of(
                            "field", "apartmentId",
                            "reason", "아파트 ID는 1 이상의 숫자여야 합니다."
                    )
            );
        }
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.APARTMENT_NOT_FOUND,
                                Map.of("apartmentId", apartmentId)
                        )
                );
        ApartmentTransaction latestTransaction =
                apartmentTransactionRepository
                        .findLatestNormalByApartmentIds(List.of(apartmentId))
                        .stream()
                        .findFirst()
                        .orElse(null);
        return ApartmentDetailResponse.of(
                apartment,
                latestTransaction,
                Math.toIntExact(
                        studyRepository.countRecruitingByApartmentId(apartmentId)
                ),
                Math.toIntExact(
                        apartmentReportQueryRepository.countByApartmentId(apartmentId)
                ),
                apartmentFavoriteRepository
                        .existsByMemberIdAndApartmentId(memberId, apartmentId),
                resolveImageUrl(apartmentId)
        );
    }

    private String resolveImageUrl(Long apartmentId) {
        if (apartmentImageUrlResolver == null) {
            return null;
        }
        return apartmentImageUrlResolver.resolve(
                apartmentRepository.findImageObjectKeyByApartmentId(apartmentId)
                        .orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ApartmentTransactionResponse> getTransactions(
            Long apartmentId,
            ApartmentTransactionSearchCondition condition,
            int page,
            int size
    ) {
        if (!apartmentRepository.existsById(apartmentId)) {
            throw new BusinessException(
                    ErrorCode.APARTMENT_NOT_FOUND,
                    java.util.Map.of("apartmentId", apartmentId)
            );
        }

        Page<ApartmentTransactionResponse> transactions = apartmentTransactionRepository.search(
                        apartmentId,
                        condition.areaMin(),
                        condition.areaMax(),
                        condition.startDate(),
                        condition.endDate(),
                        PageRequest.of(page, size, condition.sort().toSort())
                )
                .map(ApartmentTransactionResponse::from);
        return PageResponse.from(transactions);
    }

    @Transactional
    public ApartmentFavoriteResult addFavorite(
            Long memberId,
            Long apartmentId
    ) {
        validateFavoriteTarget(memberId, apartmentId);

        if (apartmentFavoriteRepository.existsByMemberIdAndApartmentId(
                memberId,
                apartmentId
        )) {
            return favoriteResult(
                    ApartmentResponseCode.APARTMENT_FAVORITE_ALREADY_EXISTS,
                    apartmentId,
                    true
            );
        }

        try {
            saveFavoriteInNewTransaction(memberId, apartmentId);
        } catch (DataIntegrityViolationException exception) {
            return favoriteResult(
                    ApartmentResponseCode.APARTMENT_FAVORITE_ALREADY_EXISTS,
                    apartmentId,
                    true
            );
        }

        return favoriteResult(
                ApartmentResponseCode.APARTMENT_FAVORITE_SUCCESS,
                apartmentId,
                true
        );
    }

    @Transactional
    public ApartmentFavoriteResult removeFavorite(
            Long memberId,
            Long apartmentId
    ) {
        validateFavoriteTarget(memberId, apartmentId);

        long deleted = apartmentFavoriteRepository
                .deleteByMemberIdAndApartmentId(memberId, apartmentId);
        apartmentFavoriteRepository.flush();

        return favoriteResult(
                deleted > 0
                        ? ApartmentResponseCode.APARTMENT_UNFAVORITE_SUCCESS
                        : ApartmentResponseCode.APARTMENT_FAVORITE_NOT_FOUND,
                apartmentId,
                false
        );
    }

    private void validateFavoriteTarget(Long memberId, Long apartmentId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!apartmentRepository.existsById(apartmentId)) {
            throw new BusinessException(ErrorCode.APARTMENT_NOT_FOUND);
        }
    }

    private void saveFavoriteInNewTransaction(
            Long memberId,
            Long apartmentId
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        transactionTemplate.executeWithoutResult(status ->
                apartmentFavoriteRepository.saveAndFlush(
                        ApartmentFavorite.of(memberId, apartmentId)
                )
        );
    }

    private ApartmentFavoriteResult favoriteResult(
            ApartmentResponseCode responseCode,
            Long apartmentId,
            boolean favoritedByMe
    ) {
        return new ApartmentFavoriteResult(
                responseCode,
                ApartmentFavoriteResponse.of(
                        apartmentId,
                        favoritedByMe,
                        apartmentFavoriteRepository.countByApartmentId(
                                apartmentId
                        )
                )
        );
    }
}
