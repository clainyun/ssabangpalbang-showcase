package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FieldPhotoValidator {

    private final FileMetaRepository fileMetaRepository;

    public FileMeta requireValidFieldPhoto(
            Long photoFileId,
            Long memberId,
            Long studyId
    ) {
        if (photoFileId == null) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_PHOTO_INVALID);
        }
        FileMeta file = fileMetaRepository.findById(photoFileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));

        if (!memberId.equals(file.getOwnerId())
                || !studyId.equals(file.getStudyId())
                || file.getFileUsage() != FileUsage.FIELD_PHOTO
                || file.getUploadStatus() != UploadStatus.COMPLETED
                || file.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.FIELD_RECORD_PHOTO_INVALID);
        }
        return file;
    }
}
