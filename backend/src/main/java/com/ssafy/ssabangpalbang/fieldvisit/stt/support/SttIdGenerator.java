package com.ssafy.ssabangpalbang.fieldvisit.stt.support;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SttIdGenerator {

    public String generate() {
        return "stt-" + UUID.randomUUID();
    }
}
