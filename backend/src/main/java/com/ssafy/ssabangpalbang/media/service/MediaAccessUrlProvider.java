package com.ssafy.ssabangpalbang.media.service;

import java.util.Collection;
import java.util.Map;

public interface MediaAccessUrlProvider {

    MediaAccessUrl issue(Long fileId);

    Map<Long, MediaAccessUrl> issueAll(Collection<Long> fileIds);
}
