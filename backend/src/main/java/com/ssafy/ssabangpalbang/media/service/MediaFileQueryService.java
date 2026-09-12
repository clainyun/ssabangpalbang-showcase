package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaFileQueryService implements MediaFileQueryPort {

    private final FileMetaRepository fileMetaRepository;

    @Override
    public List<MediaFileSnapshot> findAllById(List<Long> fileIds) {
        return fileMetaRepository.findAllById(fileIds)
                .stream()
                .map(MediaFileQueryService::toSnapshot)
                .toList();
    }

    @Override
    public List<MediaFileSnapshot> findAllByIdInForUpdate(
            List<Long> sortedFileIds
    ) {
        return fileMetaRepository.findAllByIdInForUpdate(sortedFileIds)
                .stream()
                .map(MediaFileQueryService::toSnapshot)
                .toList();
    }

    private static MediaFileSnapshot toSnapshot(FileMeta file) {
        return new MediaFileSnapshot(
                file.getId(),
                file.getOwnerId(),
                file.getFileUsage(),
                file.getOriginalName(),
                file.getContentType(),
                file.getUploadStatus(),
                file.getExpiresAt(),
                file.getDeletedAt()
        );
    }
}
