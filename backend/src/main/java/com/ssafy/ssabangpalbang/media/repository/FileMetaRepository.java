package com.ssafy.ssabangpalbang.media.repository;

import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * FileMeta 조회 전용 Repository.
 *
 * 파일 메타데이터의 생성·변경·삭제 기능은 노출하지 않고,
 * 조회 및 동시성 제어에 필요한 메서드만 제공한다.
 */
public interface FileMetaRepository extends Repository<FileMeta, Long> {

    FileMeta save(FileMeta fileMeta);

    Optional<FileMeta> findById(Long id);

    List<FileMeta> findAllById(Iterable<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT file
            FROM FileMeta file
            WHERE file.id IN :fileIds
            ORDER BY file.id ASC
            """)
    List<FileMeta> findAllByIdInForUpdate(
            @Param("fileIds") Collection<Long> fileIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT file
            FROM FileMeta file
            WHERE file.id = :fileId
            """)
    Optional<FileMeta> findByIdForUpdate(@Param("fileId") Long fileId);
}
