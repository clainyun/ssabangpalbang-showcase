package com.ssafy.ssabangpalbang.apartment.dataload;

import com.ssafy.ssabangpalbang.apartment.dataload.client.AptBasisInfoClient;
import com.ssafy.ssabangpalbang.apartment.dataload.client.AptListClient;
import com.ssafy.ssabangpalbang.apartment.dataload.client.KakaoGeocodingClient;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptBasisInfo;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptDetailInfo;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptListItem;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.Coordinate;
import com.ssafy.ssabangpalbang.apartment.dataload.support.ApartmentFieldMapper;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Profile("dataload")
public class ApartmentDataLoadService {

    private static final Logger log = LoggerFactory.getLogger(ApartmentDataLoadService.class);

    private final ApartmentRepository apartmentRepository;
    private final AptListClient listClient;
    private final AptBasisInfoClient basisInfoClient;
    private final KakaoGeocodingClient geocodingClient;
    private final DataLoadProperties properties;
    private final ObjectProvider<ApartmentDataLoadService> selfProvider;

    public ApartmentDataLoadService(
            ApartmentRepository apartmentRepository,
            AptListClient listClient,
            AptBasisInfoClient basisInfoClient,
            KakaoGeocodingClient geocodingClient,
            DataLoadProperties properties,
            ObjectProvider<ApartmentDataLoadService> selfProvider
    ) {
        this.apartmentRepository = apartmentRepository;
        this.listClient = listClient;
        this.basisInfoClient = basisInfoClient;
        this.geocodingClient = geocodingClient;
        this.properties = properties;
        this.selfProvider = selfProvider;
    }

    public void load() {
        if (properties.getSigunguCodes() == null || properties.getSigunguCodes().isEmpty()) {
            log.warn("[dataload] sigungu-codes가 비어 있어 적재하지 않습니다.");
            return;
        }
        for (String code : properties.getSigunguCodes()) {
            if (code == null || !code.matches("\\d{5}")) {
                log.warn("[dataload] 유효하지 않은 시군구 코드 건너뜀: {}", code);
                continue;
            }
            try {
                loadDistrict(code);
            } catch (RuntimeException exception) {
                log.warn("[dataload] {} 시군구 처리 실패: {}", code, exception.getMessage());
            }
        }
    }

    private void loadDistrict(String code) {
        int listCallsBefore = listClient.getCallCount();
        int basicCallsBefore = basisInfoClient.getBasicCallCount();
        int detailCallsBefore = basisInfoClient.getDetailCallCount();
        int geocodingCallsBefore = geocodingClient.getCallCount();
        List<AptListItem> apartments = listClient.findBySigunguCode(code);
        if (apartments.isEmpty()) {
            log.warn("[dataload] {} 단지목록이 비어 있어 건너뜁니다.", code);
            return;
        }

        int inserted = 0;
        int updated = 0;
        int basicFailures = 0;
        int saveFailures = 0;
        int processed = 0;
        List<String> coordinateFailures = new ArrayList<>();

        log.info("[dataload] {} 처리 시작 · 대상 {}개", code, apartments.size());
        for (AptListItem item : apartments) {
            try {
                Optional<AptBasisInfo> basic = basisInfoClient.getBasic(item.kaptCode());
                if (basic.isEmpty()) {
                    basicFailures++;
                    continue;
                }
                AptDetailInfo detail = basisInfoClient.getDetail(item.kaptCode())
                        .orElse(new AptDetailInfo(null, null));
                Optional<Coordinate> coordinate = findCoordinate(basic.get());
                if (coordinate.isEmpty()) {
                    coordinateFailures.add(item.kaptName());
                    continue;
                }
                ApartmentMaster master = toMaster(item, basic.get(), detail, coordinate.get());
                SaveResult result = properties.isDryRun()
                        ? selfProvider.getObject().inspect(master.complexCode())
                        : selfProvider.getObject().saveOne(master);
                if (result == SaveResult.INSERTED) {
                    inserted++;
                } else {
                    updated++;
                }
            } catch (RuntimeException exception) {
                saveFailures++;
                log.warn("[dataload] 단지 처리 실패({}): {}", item.kaptName(), exception.getMessage());
            } finally {
                processed++;
                if (processed % 10 == 0 || processed == apartments.size()) {
                    int progressPercent = processed * 100 / apartments.size();
                    log.info("[dataload] {} 진행 {}/{} ({}%)",
                            code, processed, apartments.size(), progressPercent);
                }
            }
        }

        String districtName = apartments.get(0).as2();
        int listCalls = listClient.getCallCount() - listCallsBefore;
        int basicCalls = basisInfoClient.getBasicCallCount() - basicCallsBefore;
        int detailCalls = basisInfoClient.getDetailCallCount() - detailCallsBefore;
        int geocodingCalls = geocodingClient.getCallCount() - geocodingCallsBefore;
        int calls = listCalls + basicCalls + detailCalls + geocodingCalls;
        log.info("[dataload] {} {} · 단지 {}개", code, districtName, listClient.getLastTotalCount());
        log.info("[dataload]   INSERT {} · UPDATE {} · SKIP {}",
                inserted, updated, coordinateFailures.size() + basicFailures + saveFailures);
        log.info("[dataload]   좌표 실패 {}건: {}", coordinateFailures.size(), String.join(", ", coordinateFailures));
        log.info("[dataload]   기본정보 조회 실패 {}건", basicFailures);
        log.info("[dataload]   API 호출 {}회 (목록 {} · 기본 {} · 상세 {} · 좌표 {})",
                calls, listCalls, basicCalls, detailCalls, geocodingCalls);
    }

    private Optional<Coordinate> findCoordinate(AptBasisInfo basic) {
        Optional<Coordinate> coordinate = geocodingClient.find(basic.doroJuso());
        if (coordinate.isPresent()) {
            return coordinate;
        }
        if (basic.kaptAddr() == null || basic.kaptAddr().equals(basic.doroJuso())) {
            return Optional.empty();
        }
        return geocodingClient.find(basic.kaptAddr());
    }

    private ApartmentMaster toMaster(
            AptListItem item,
            AptBasisInfo basic,
            AptDetailInfo detail,
            Coordinate coordinate
    ) {
        String address = basic.doroJuso() == null ? basic.kaptAddr() : basic.doroJuso();
        return new ApartmentMaster(
                item.kaptCode(), item.kaptName(), address,
                ApartmentFieldMapper.districtCode(item.bjdCode()),
                item.as2(), item.as3(),
                ApartmentFieldMapper.legalDongCode(item.bjdCode()),
                coordinate.longitude(), coordinate.latitude(),
                ApartmentFieldMapper.householdCount(basic.kaptdaCnt()),
                ApartmentFieldMapper.completionYearMonth(basic.kaptUsedate()),
                ApartmentFieldMapper.parkingSpaceCount(detail.kaptdPcnt(), detail.kaptdPcntu())
        );
    }

    @Transactional
    public SaveResult saveOne(ApartmentMaster master) {
        Optional<Apartment> existing = apartmentRepository.findByComplexCode(master.complexCode());
        if (existing.isPresent()) {
            Apartment apartment = existing.get();
            apartment.updateMasterInfo(
                    master.name(), master.address(), master.districtCode(), master.districtName(),
                    master.dongName(), master.legalDongCode(), master.longitude(), master.latitude(),
                    master.householdCount(), master.completionYearMonth(), master.parkingSpaceCount()
            );
            return SaveResult.UPDATED;
        }
        apartmentRepository.save(Apartment.create(
                master.complexCode(), master.name(), master.address(),
                master.districtCode(), master.districtName(), master.dongName(),
                master.legalDongCode(), master.longitude(), master.latitude(),
                master.householdCount(), master.completionYearMonth(), master.parkingSpaceCount()
        ));
        return SaveResult.INSERTED;
    }

    @Transactional(readOnly = true)
    public SaveResult inspect(String complexCode) {
        return apartmentRepository.findByComplexCode(complexCode).isPresent()
                ? SaveResult.UPDATED : SaveResult.INSERTED;
    }

    public enum SaveResult {
        INSERTED, UPDATED
    }

    public record ApartmentMaster(
            String complexCode, String name, String address,
            String districtCode, String districtName, String dongName,
            String legalDongCode, Double longitude, Double latitude,
            Integer householdCount, String completionYearMonth, Integer parkingSpaceCount
    ) {
    }
}
