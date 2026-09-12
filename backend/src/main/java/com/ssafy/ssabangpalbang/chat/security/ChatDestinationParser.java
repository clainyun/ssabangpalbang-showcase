package com.ssafy.ssabangpalbang.chat.security;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP SUBSCRIBE·SEND 목적지 문자열에서 studyId를 추출한다.
 *
 * <p>허용 경로는 다음 두 가지로 고정한다.</p>
 * <ul>
 *     <li>SUBSCRIBE: /sub/studies/{studyId}/chat</li>
 *     <li>SEND: /pub/studies/{studyId}/chat/messages</li>
 * </ul>
 */
public final class ChatDestinationParser {

    private static final Pattern SUBSCRIBE_PATTERN =
            Pattern.compile("^/sub/studies/(\\d+)/chat$");
    private static final Pattern SEND_PATTERN =
            Pattern.compile("^/pub/studies/(\\d+)/chat/messages$");

    private ChatDestinationParser() {
    }

    public static Long parseChatSubscribeStudyId(String destination) {
        return parse(destination, SUBSCRIBE_PATTERN);
    }

    public static Long parseChatSendStudyId(String destination) {
        return parse(destination, SEND_PATTERN);
    }

    private static Long parse(String destination, Pattern pattern) {
        if (destination == null) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }

        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
        }

        return Long.valueOf(matcher.group(1));
    }
}
