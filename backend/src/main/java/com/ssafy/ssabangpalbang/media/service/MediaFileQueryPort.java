package com.ssafy.ssabangpalbang.media.service;

import java.util.List;

public interface MediaFileQueryPort {

    List<MediaFileSnapshot> findAllById(List<Long> fileIds);

    List<MediaFileSnapshot> findAllByIdInForUpdate(
            List<Long> sortedFileIds
    );
}
