package com.ssafy.ssabangpalbang.demoseed;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 리허설로 더러워진 데이터를 초기 상태로 되돌린다.
 *
 * <p><b>왜 "데모 계정만 골라 삭제"가 아닌가.</b> member(id)·study(id)를 참조하는 테이블이
 * 30개가 넘고 팀이 계속 추가하고 있다. 손으로 삭제 순서를 관리하면 테이블이 하나 늘어날 때마다
 * 조용히 깨지고, 남은 고아 행이 다음 시연에서 터진다. 그래서 <b>앱 데이터 전체를 비우고</b>
 * 다시 시딩한다. 대상 목록은 런타임에 {@code pg_tables}에서 읽으므로 새 테이블도 자동 포함된다.</p>
 *
 * <p>{@code TRUNCATE ... CASCADE}를 한 문장으로 실행해 FK 순서를 Postgres가 풀게 한다.</p>
 *
 * <p><b>보존 대상</b>은 다시 만드는 비용이 큰 참조 데이터뿐이다 — 아파트 마스터·실거래·이미지,
 * 아파트 RAG 색인, Flyway 이력, PostGIS 시스템 테이블.</p>
 */
@Service
@Profile("demoseed")
public class DemoSeedResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedResetService.class);

    /** 오조작 방지 토큰. {@code demoseed.reset-token} 이 정확히 이 값이어야 한다. */
    static final String RESET_TOKEN = "WIPE-AND-RESEED";

    /** 비우지 않는 테이블. 재적재·재색인 비용이 크거나 시스템 소유다. */
    private static final Set<String> PRESERVED_TABLES = Set.of(
            "flyway_schema_history",
            "spatial_ref_sys",
            "apartment",
            "apartment_transaction",
            "apartment_image",
            "apartment_rag_document"
    );

    /**
     * RAG 색인 테이블은 통째로 보존하지만(아파트 문서 재색인 비용이 크다),
     * <b>리포트 출처 청크는 지워야 한다.</b> 리포트 행은 TRUNCATE로 사라지는데 청크를 남기면
     * 없는 리포트를 가리키는 고아가 되고, 챗봇이 그것을 근거로 답한다. 실제로 발견한 문제다.
     */
    private static final String DELETE_REPORT_RAG_DOCUMENTS = """
            DELETE FROM apartment_rag_document WHERE source_type = 'REPORT'
            """;

    private final EntityManager entityManager;

    public DemoSeedResetService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void reset(String suppliedToken) {
        if (!RESET_TOKEN.equals(suppliedToken)) {
            throw new IllegalStateException(
                    "리셋은 앱 데이터 전체를 비웁니다. 실행하려면 --demoseed.reset-token="
                            + RESET_TOKEN + " 을 함께 넘기십시오."
            );
        }

        List<String> targets = truncationTargets();
        if (targets.isEmpty()) {
            log.warn("[demoseed] 비울 테이블이 없습니다. 스키마가 비어 있는지 확인하십시오.");
            return;
        }

        String tableList = String.join(", ", targets);
        log.warn(
                "[demoseed] 앱 데이터 {}개 테이블을 비웁니다. 보존: {}",
                targets.size(),
                String.join(", ", PRESERVED_TABLES)
        );

        entityManager
                .createNativeQuery("TRUNCATE TABLE " + tableList + " RESTART IDENTITY CASCADE")
                .executeUpdate();

        int orphanChunks = entityManager
                .createNativeQuery(DELETE_REPORT_RAG_DOCUMENTS)
                .executeUpdate();

        log.info(
                "[demoseed] 초기화를 완료했습니다. 리포트 RAG 청크 {}건도 함께 삭제했습니다.",
                orphanChunks
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> truncationTargets() {
        List<String> tables = entityManager
                .createNativeQuery("""
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname = 'public'
                        ORDER BY tablename
                        """)
                .getResultList();

        return tables.stream()
                .filter(name -> !PRESERVED_TABLES.contains(name))
                // 식별자는 pg_tables 에서 온 값이지만 방어적으로 이름 형식을 제한한다.
                .filter(name -> name.matches("[a-z_][a-z0-9_]*"))
                .toList();
    }
}
