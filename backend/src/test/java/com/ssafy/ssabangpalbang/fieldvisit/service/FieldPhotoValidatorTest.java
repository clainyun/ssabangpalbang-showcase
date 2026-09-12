package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldPhotoValidatorTest {

    private FileMetaRepository fileMetaRepository;
    private FieldPhotoValidator validator;

    @BeforeEach
    void setUp() {
        fileMetaRepository = mock(FileMetaRepository.class);
        validator = new FieldPhotoValidator(fileMetaRepository);
    }

    @Test
    void 유효한_FIELD_PHOTO면_통과한다() {
        FileMeta file = mockFile(10L, 42L, 7L, FileUsage.FIELD_PHOTO, UploadStatus.COMPLETED, null);
        when(fileMetaRepository.findById(10L)).thenReturn(Optional.of(file));

        assertThat(validator.requireValidFieldPhoto(10L, 42L, 7L)).isSameAs(file);
    }

    @Test
    void 소유자가_다르면_실패한다() {
        FileMeta file = mockFile(10L, 99L, 7L, FileUsage.FIELD_PHOTO, UploadStatus.COMPLETED, null);
        when(fileMetaRepository.findById(10L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> validator.requireValidFieldPhoto(10L, 42L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_PHOTO_INVALID);
    }

    @Test
    void study가_다르면_실패한다() {
        FileMeta file = mockFile(10L, 42L, 8L, FileUsage.FIELD_PHOTO, UploadStatus.COMPLETED, null);
        when(fileMetaRepository.findById(10L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> validator.requireValidFieldPhoto(10L, 42L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_PHOTO_INVALID);
    }

    @Test
    void 업로드_미완료면_실패한다() {
        FileMeta file = mockFile(10L, 42L, 7L, FileUsage.FIELD_PHOTO, UploadStatus.PENDING, null);
        when(fileMetaRepository.findById(10L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> validator.requireValidFieldPhoto(10L, 42L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_PHOTO_INVALID);
    }

    @Test
    void 삭제된_파일이면_실패한다() {
        FileMeta file = mockFile(
                10L, 42L, 7L, FileUsage.FIELD_PHOTO, UploadStatus.COMPLETED, Instant.now()
        );
        when(fileMetaRepository.findById(10L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> validator.requireValidFieldPhoto(10L, 42L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_RECORD_PHOTO_INVALID);
    }

    private static FileMeta mockFile(
            Long id,
            Long ownerId,
            Long studyId,
            FileUsage usage,
            UploadStatus status,
            Instant deletedAt
    ) {
        FileMeta file = mock(FileMeta.class);
        when(file.getId()).thenReturn(id);
        when(file.getOwnerId()).thenReturn(ownerId);
        when(file.getStudyId()).thenReturn(studyId);
        when(file.getFileUsage()).thenReturn(usage);
        when(file.getUploadStatus()).thenReturn(status);
        when(file.getDeletedAt()).thenReturn(deletedAt);
        return file;
    }
}
