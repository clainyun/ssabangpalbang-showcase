package com.ssafy.ssabangpalbang.apartment.dataload;

import com.ssafy.ssabangpalbang.apartment.dataload.client.AptTradeClient;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptTradeItem;
import com.ssafy.ssabangpalbang.apartment.dataload.support.RoadAddressKey;
import com.ssafy.ssabangpalbang.apartment.dataload.support.TransactionFieldMapper;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.domain.ApartmentTransaction;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Profile("dataload-tx")
public class ApartmentTransactionDataLoadService {

    private static final Logger log = LoggerFactory.getLogger(ApartmentTransactionDataLoadService.class);
    private static final int MAX_FAILED_NAMES = 20;

    private final ApartmentRepository apartmentRepository;
    private final ApartmentTransactionRepository transactionRepository;
    private final AptTradeClient client;
    private final DataLoadProperties properties;
    private final ObjectProvider<ApartmentTransactionDataLoadService> selfProvider;
    private final Set<String> loggedCancellationTypes = new LinkedHashSet<>();

    public ApartmentTransactionDataLoadService(
            ApartmentRepository apartmentRepository,
            ApartmentTransactionRepository transactionRepository,
            AptTradeClient client,
            DataLoadProperties properties,
            ObjectProvider<ApartmentTransactionDataLoadService> selfProvider
    ) {
        this.apartmentRepository = apartmentRepository;
        this.transactionRepository = transactionRepository;
        this.client = client;
        this.properties = properties;
        this.selfProvider = selfProvider;
    }

    public void load() {
        if (isEmpty(properties.getSigunguCodes()) || isEmpty(properties.getDealYearMonths())) {
            log.warn("[dataload-tx] sigungu-codes 또는 deal-year-months가 비어 있어 적재하지 않습니다.");
            return;
        }
        for (String sigunguCode : properties.getSigunguCodes()) {
            if (sigunguCode == null || !sigunguCode.matches("\\d{5}")) {
                log.warn("[dataload-tx] 유효하지 않은 시군구 코드 건너뜀: {}", sigunguCode);
                continue;
            }
            Map<String, Long> apartmentIndex = apartmentIndex(sigunguCode);
            for (String dealYearMonth : properties.getDealYearMonths()) {
                if (dealYearMonth == null || !dealYearMonth.matches("\\d{4}(0[1-9]|1[0-2])")) {
                    log.warn("[dataload-tx] 유효하지 않은 계약연월 건너뜀: {}", dealYearMonth);
                    continue;
                }
                try {
                    loadCombination(sigunguCode, dealYearMonth, apartmentIndex);
                } catch (RuntimeException exception) {
                    log.warn("[dataload-tx] {} {} 처리 실패: {}",
                            sigunguCode, dealYearMonth, exception.getMessage());
                }
            }
        }
    }

    private Map<String, Long> apartmentIndex(String sigunguCode) {
        return apartmentRepository.findByDistrictCode(sigunguCode).stream()
                .filter(apartment -> !RoadAddressKey.fromFullAddress(apartment.getAddress()).isEmpty())
                .collect(Collectors.toMap(
                        apartment -> RoadAddressKey.fromFullAddress(apartment.getAddress()),
                        Apartment::getId,
                        (first, ignored) -> first
                ));
    }

    private void loadCombination(String sigunguCode, String dealYearMonth, Map<String, Long> apartmentIndex) {
        int callsBefore = client.getCallCount();
        List<AptTradeItem> items = client.find(sigunguCode, dealYearMonth);
        int inserted = 0;
        int duplicated = 0;
        int matchFailed = 0;
        int canceled = 0;
        int failed = 0;
        Set<String> failedNames = new LinkedHashSet<>();

        for (AptTradeItem item : items) {
            try {
                if (TransactionFieldMapper.canceled(item.cancellationType())) {
                    canceled++;
                    logCancellationType(item.cancellationType());
                }
                String addressKey = RoadAddressKey.fromParts(
                        item.roadName(), item.roadMainNumber(), item.roadSubNumber());
                Long apartmentId = apartmentIndex.get(addressKey);
                if (apartmentId == null) {
                    matchFailed++;
                    if (failedNames.size() < MAX_FAILED_NAMES && item.aptName() != null) {
                        failedNames.add(item.aptName());
                    }
                    continue;
                }
                LocalDate dealDate = TransactionFieldMapper.dealDate(
                        item.dealYear(), item.dealMonth(), item.dealDay());
                if (dealDate == null) {
                    continue;
                }
                String dedupKey = TransactionFieldMapper.dedupKey(item);
                if (dedupKey.length() > 200) {
                    log.warn("[dataload-tx] dedup_key 200자 초과로 거래 건너뜀: {}", item.aptName());
                    continue;
                }
                TransactionData data = new TransactionData(
                        apartmentId, dealDate,
                        TransactionFieldMapper.exclusiveArea(item.exclusiveArea()),
                        TransactionFieldMapper.price(item.dealAmount()),
                        TransactionFieldMapper.floor(item.floor()),
                        TransactionFieldMapper.canceled(item.cancellationType()),
                        dedupKey
                );
                SaveResult result = properties.isDryRun()
                        ? selfProvider.getObject().inspect(data.dedupKey())
                        : selfProvider.getObject().saveOne(data);
                if (result == SaveResult.INSERTED) {
                    inserted++;
                } else {
                    duplicated++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("[dataload-tx] 거래 처리 실패({}): {}", item.aptName(), exception.getMessage());
            }
        }

        log.info("[dataload-tx] {} {} · 거래 {}건", sigunguCode, dealYearMonth, client.getLastTotalCount());
        log.info("[dataload-tx]   INSERT {} · 중복 {} · 매칭실패 {}", inserted, duplicated, matchFailed);
        log.info("[dataload-tx]   해제 거래 {}건", canceled);
        log.info("[dataload-tx]   매칭 실패 단지(상위 20): {}", String.join(", ", failedNames));
        log.info("[dataload-tx]   개별 처리 실패 {}건", failed);
        log.info("[dataload-tx]   API 호출 {}회", client.getCallCount() - callsBefore);
    }

    private void logCancellationType(String value) {
        String normalized = value.trim();
        if (loggedCancellationTypes.add(normalized)) {
            log.info("[dataload-tx] 확인된 cdealType 값: {}", normalized);
        }
    }

    @Transactional
    public SaveResult saveOne(TransactionData data) {
        if (transactionRepository.existsByDedupKey(data.dedupKey())) {
            return SaveResult.DUPLICATED;
        }
        transactionRepository.save(ApartmentTransaction.create(
                data.apartmentId(), data.dealDate(), data.exclusiveArea(), data.price(),
                data.floor(), data.canceled(), data.dedupKey()
        ));
        return SaveResult.INSERTED;
    }

    @Transactional(readOnly = true)
    public SaveResult inspect(String dedupKey) {
        return transactionRepository.existsByDedupKey(dedupKey)
                ? SaveResult.DUPLICATED : SaveResult.INSERTED;
    }

    private boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    public enum SaveResult {
        INSERTED, DUPLICATED
    }

    public record TransactionData(
            Long apartmentId, LocalDate dealDate, java.math.BigDecimal exclusiveArea,
            Long price, Integer floor, boolean canceled, String dedupKey
    ) {
    }
}
