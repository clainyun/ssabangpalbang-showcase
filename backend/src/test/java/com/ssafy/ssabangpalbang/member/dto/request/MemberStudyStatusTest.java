package com.ssafy.ssabangpalbang.member.dto.request;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberStudyStatusTest {

    @Test
    void 명세에_정의된_상태를_변환한다() {
        assertThat(MemberStudyStatus.from("ALL"))
                .isEqualTo(MemberStudyStatus.ALL);
        assertThat(MemberStudyStatus.from("ACTIVE"))
                .isEqualTo(MemberStudyStatus.ACTIVE);
        assertThat(MemberStudyStatus.from("IN_PROGRESS"))
                .isEqualTo(MemberStudyStatus.IN_PROGRESS);
        assertThat(MemberStudyStatus.from("COMPLETED"))
                .isEqualTo(MemberStudyStatus.COMPLETED);
    }

    @Test
    void 허용되지_않은_상태면_명세_오류를_반환한다() {
        assertThatThrownBy(() -> MemberStudyStatus.from("READY"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.MEMBER_STUDY_STATUS_INVALID
                                )
                );
    }
}
