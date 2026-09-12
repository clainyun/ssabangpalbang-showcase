package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecord;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * field_record를 REQUIRES_NEW로 저장한다.
 * UNIQUE(client_request_id) 충돌은 호출자가 재조회한다.
 */
@Component
@RequiredArgsConstructor
public class FieldRecordWriter {

    private final FieldRecordRepository fieldRecordRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FieldRecord saveAndFlush(FieldRecord record) {
        return fieldRecordRepository.saveAndFlush(record);
    }
}
