package com.ssafy.ssabangpalbang.media.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S3 객체 조회용 Presigned GET URL을 발급한다.
 *
 * <p>공통 미디어 Provider의 파일 상태·삭제·만료 검증과 배치 발급을
 * 채팅 도메인에 맞는 URL 형태로 노출하는 얇은 어댑터다.</p>
 */
@Service
@RequiredArgsConstructor
public class MediaPresignedUrlProvider {

    private final GatewayMediaAccessUrlProvider mediaAccessUrlProvider;

    public boolean isEnabled() {
        return mediaAccessUrlProvider.isEnabled();
    }

    public String generateGetUrl(Long fileId) {
        return mediaAccessUrlProvider.issue(fileId).url();
    }

    public Map<Long, String> generateGetUrls(
            Collection<Long> fileIds
    ) {
        Map<Long, String> urls = new LinkedHashMap<>();
        for (Map.Entry<Long, MediaAccessUrl> entry
                : mediaAccessUrlProvider.issueAll(fileIds)
                .entrySet()) {
            urls.put(entry.getKey(), entry.getValue().url());
        }
        return Map.copyOf(urls);
    }
}
