package com.ssafy.ssabangpalbang.fieldvisit.domain;

/**
 * field_record.source_type 값이다.
 */
public enum FieldRecordSourceType {
    TEXT,
    PHOTO,
    STT;

    public static FieldRecordSourceType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sourceType is required");
        }
        return FieldRecordSourceType.valueOf(value.trim().toUpperCase());
    }
}
