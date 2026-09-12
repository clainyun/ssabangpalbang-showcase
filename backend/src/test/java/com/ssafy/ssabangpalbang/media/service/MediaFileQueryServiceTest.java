package com.ssafy.ssabangpalbang.media.service;

import com.ssafy.ssabangpalbang.media.domain.FileMeta;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.repository.FileMetaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaFileQueryServiceTest {

    @Mock
    private FileMetaRepository fileMetaRepository;

    @InjectMocks
    private MediaFileQueryService mediaFileQueryService;

    @Test
    void returnsSnapshotsWithoutExposingS3Key() {
        FileMeta file = BeanUtils.instantiateClass(FileMeta.class);
        ReflectionTestUtils.setField(file, "id", 401L);
        ReflectionTestUtils.setField(file, "ownerId", 7L);
        ReflectionTestUtils.setField(
                file,
                "fileUsage",
                FileUsage.POST_ATTACHMENT
        );
        ReflectionTestUtils.setField(
                file,
                "uploadStatus",
                UploadStatus.COMPLETED
        );
        ReflectionTestUtils.setField(
                file,
                "originalName",
                "station-route.jpg"
        );
        ReflectionTestUtils.setField(
                file,
                "contentType",
                "image/jpeg"
        );
        ReflectionTestUtils.setField(
                file,
                "s3Key",
                "private/post/401.jpg"
        );
        when(fileMetaRepository.findAllByIdInForUpdate(
                List.of(401L)
        )).thenReturn(List.of(file));

        List<MediaFileSnapshot> result =
                mediaFileQueryService.findAllByIdInForUpdate(
                        List.of(401L)
                );

        assertThat(result).containsExactly(new MediaFileSnapshot(
                401L,
                7L,
                FileUsage.POST_ATTACHMENT,
                "station-route.jpg",
                "image/jpeg",
                UploadStatus.COMPLETED,
                null,
                null
        ));
        assertThat(MediaFileSnapshot.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("s3Key");
        verify(fileMetaRepository).findAllByIdInForUpdate(
                List.of(401L)
        );
    }
}
