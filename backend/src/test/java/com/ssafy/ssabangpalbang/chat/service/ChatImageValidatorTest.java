package com.ssafy.ssabangpalbang.chat.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import com.ssafy.ssabangpalbang.media.service.MediaPresignedUrlProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatImageValidatorTest {

    private static final Long STUDY_ID = 7L;
    private static final Long SENDER_ID = 42L;
    private static final Long FILE_ID = 91L;

    @Mock
    private FileMetaRepository fileMetaRepository;

    @Mock
    private MediaPresignedUrlProvider mediaPresignedUrlProvider;

    private ChatImageValidator chatImageValidator;

    @BeforeEach
    void setUp() {
        chatImageValidator = new ChatImageValidator(fileMetaRepository, mediaPresignedUrlProvider);
        lenient().when(mediaPresignedUrlProvider.isEnabled()).thenReturn(true);
    }

    @Test
    void imageFileId가_없으면_CHAT_IMAGE_INVALID() {
        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void S3가_비활성화면_CHAT_IMAGE_INVALID() {
        when(mediaPresignedUrlProvider.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void 파일이_존재하지_않으면_CHAT_IMAGE_INVALID() {
        when(fileMetaRepository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void fileUsage가_CHAT_IMAGE가_아니면_CHAT_IMAGE_INVALID() {
        FileMeta fileMeta = fileMeta(FileUsage.FIELD_PHOTO, UploadStatus.COMPLETED, SENDER_ID, STUDY_ID, null);
        when(fileMetaRepository.findById(FILE_ID)).thenReturn(Optional.of(fileMeta));

        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void uploadStatus가_COMPLETED가_아니면_CHAT_IMAGE_INVALID() {
        FileMeta fileMeta = fileMeta(FileUsage.CHAT_IMAGE, UploadStatus.PENDING, SENDER_ID, STUDY_ID, null);
        when(fileMetaRepository.findById(FILE_ID)).thenReturn(Optional.of(fileMeta));

        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void 삭제된_파일이면_CHAT_IMAGE_INVALID() {
        FileMeta fileMeta = fileMeta(FileUsage.CHAT_IMAGE, UploadStatus.COMPLETED, SENDER_ID, STUDY_ID, Instant.now());
        when(fileMetaRepository.findById(FILE_ID)).thenReturn(Optional.of(fileMeta));

        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void ownerId가_발신자와_다르면_CHAT_IMAGE_INVALID() {
        FileMeta fileMeta = fileMeta(FileUsage.CHAT_IMAGE, UploadStatus.COMPLETED, 999L, STUDY_ID, null);
        when(fileMetaRepository.findById(FILE_ID)).thenReturn(Optional.of(fileMeta));

        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void studyId가_파일의_studyId와_다르면_CHAT_IMAGE_INVALID() {
        FileMeta fileMeta = fileMeta(FileUsage.CHAT_IMAGE, UploadStatus.COMPLETED, SENDER_ID, 999L, null);
        when(fileMetaRepository.findById(FILE_ID)).thenReturn(Optional.of(fileMeta));

        assertThatThrownBy(() -> chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_IMAGE_INVALID));
    }

    @Test
    void 모든_조건을_만족하면_FileMeta를_반환한다() {
        FileMeta fileMeta = fileMeta(FileUsage.CHAT_IMAGE, UploadStatus.COMPLETED, SENDER_ID, STUDY_ID, null);
        when(fileMetaRepository.findById(FILE_ID)).thenReturn(Optional.of(fileMeta));

        FileMeta result = chatImageValidator.validate(STUDY_ID, SENDER_ID, FILE_ID);

        assertThat(result).isSameAs(fileMeta);
    }

    private FileMeta fileMeta(
            FileUsage fileUsage,
            UploadStatus uploadStatus,
            Long ownerId,
            Long studyId,
            Instant deletedAt
    ) {
        FileMeta fileMeta = BeanUtils.instantiateClass(FileMeta.class);
        ReflectionTestUtils.setField(fileMeta, "id", FILE_ID);
        ReflectionTestUtils.setField(fileMeta, "ownerId", ownerId);
        ReflectionTestUtils.setField(fileMeta, "studyId", studyId);
        ReflectionTestUtils.setField(fileMeta, "fileUsage", fileUsage);
        ReflectionTestUtils.setField(fileMeta, "s3Key", "chat/" + FILE_ID + ".jpg");
        ReflectionTestUtils.setField(fileMeta, "uploadStatus", uploadStatus);
        ReflectionTestUtils.setField(fileMeta, "deletedAt", deletedAt);
        return fileMeta;
    }
}
