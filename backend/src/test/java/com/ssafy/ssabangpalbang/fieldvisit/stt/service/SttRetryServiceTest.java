package com.ssafy.ssabangpalbang.fieldvisit.stt.service;

import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttJob;
import com.ssafy.ssabangpalbang.fieldvisit.stt.dto.request.SttRetryRequest;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobAttemptRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttJobRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttRequestQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.repository.SttStatusQueryRepository;
import com.ssafy.ssabangpalbang.fieldvisit.stt.response.SttResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.stt.support.SttIdGenerator;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.auth.LoginMember;
import com.ssafy.ssabangpalbang.member.auth.LoginMemberResolver;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SttRetryServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long STUDY_ID = 10L;
    private static final String STT_ID = "stt-retry";
    private static final String CLIENT_REQUEST_ID =
            "55591972-492e-4c29-81bd-eb203f37be49";
    private static final OffsetDateTime RETRY_REQUESTED_AT =
            OffsetDateTime.of(
                    2026,
                    7,
                    25,
                    14,
                    30,
                    0,
                    0,
                    ZoneOffset.ofHours(9)
            );

    @Mock
    private LoginMemberResolver loginMemberResolver;

    @Mock
    private StudyMemberRepository studyMemberRepository;

    @Mock
    private SttJobRepository sttJobRepository;

    @Mock
    private SttJobAttemptRepository sttJobAttemptRepository;

    @Mock
    private SttRequestQueryRepository requestQueryRepository;

    @Mock
    private SttStatusQueryRepository statusQueryRepository;

    @Mock
    private SttCreationTransaction creationTransaction;

    @Mock
    private SttRetryTransaction retryTransaction;

    @Mock
    private SttIdGenerator sttIdGenerator;

    @InjectMocks
    private SttService sttService;

    @Test
    void 신규_재처리를_202로_변환한다() {
        activeLoginMember();
        SttJob job = job();
        when(retryTransaction.retry(any())).thenReturn(
                new SttRetryTransaction.TransactionResult(
                        job,
                        RETRY_REQUESTED_AT,
                        true
                )
        );

        SttService.RetryResult result = sttService.retryStt(
                STUDY_ID,
                STT_ID,
                validRequest()
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.responseCode())
                .isEqualTo(SttResponseCode.FIELD_STT_RETRY_ACCEPTED);
        assertThat(result.response().retryRequestedAt())
                .isEqualTo(RETRY_REQUESTED_AT);

        ArgumentCaptor<SttRetryCommand> commandCaptor =
                ArgumentCaptor.forClass(SttRetryCommand.class);
        verify(retryTransaction).retry(commandCaptor.capture());
        assertThat(commandCaptor.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(commandCaptor.getValue().studyId()).isEqualTo(STUDY_ID);
        assertThat(commandCaptor.getValue().sttId()).isEqualTo(STT_ID);
        assertThat(commandCaptor.getValue().clientRequestId())
                .isEqualTo(UUID.fromString(CLIENT_REQUEST_ID));
    }

    @Test
    void 기존_재처리는_기존_시각을_유지하며_200을_반환한다() {
        activeLoginMember();
        when(retryTransaction.retry(any())).thenReturn(
                new SttRetryTransaction.TransactionResult(
                        job(),
                        RETRY_REQUESTED_AT,
                        false
                )
        );

        SttService.RetryResult result = sttService.retryStt(
                STUDY_ID,
                STT_ID,
                validRequest()
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.responseCode()).isEqualTo(
                SttResponseCode.FIELD_STT_RETRY_ALREADY_IN_PROGRESS
        );
        assertThat(result.response().retryRequestedAt())
                .isEqualTo(RETRY_REQUESTED_AT);
    }

    @Test
    void UNIQUE_경합은_동일_요청을_재조회한다() {
        activeLoginMember();
        when(retryTransaction.retry(any()))
                .thenThrow(new DataIntegrityViolationException("unique"))
                .thenReturn(new SttRetryTransaction.TransactionResult(
                        job(),
                        RETRY_REQUESTED_AT,
                        false
                ));

        SttService.RetryResult result = sttService.retryStt(
                STUDY_ID,
                STT_ID,
                validRequest()
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        verify(retryTransaction, times(2)).retry(any());
    }

    @Test
    void 축약_UUID는_트랜잭션_전에_400으로_거부한다() {
        activeLoginMember();

        assertThatThrownBy(() -> sttService.retryStt(
                STUDY_ID,
                STT_ID,
                new SttRetryRequest("1-1-1-1-1")
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
            assertThat(exception.getData())
                    .containsEntry("field", "clientRequestId");
        });

        verifyNoInteractions(retryTransaction);
    }

    @Test
    void 비활성_회원은_재처리할_수_없다() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, false));

        assertThatThrownBy(() -> sttService.retryStt(
                STUDY_ID,
                STT_ID,
                validRequest()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));

        verifyNoInteractions(retryTransaction);
    }

    private void activeLoginMember() {
        when(loginMemberResolver.resolve())
                .thenReturn(new LoginMember(MEMBER_ID, true));
    }

    private SttRetryRequest validRequest() {
        return new SttRetryRequest(CLIENT_REQUEST_ID);
    }

    private SttJob job() {
        return SttJob.create(
                STT_ID,
                MEMBER_ID,
                STUDY_ID,
                100L,
                90L,
                503L,
                UUID.randomUUID(),
                RETRY_REQUESTED_AT.minusMinutes(5)
        );
    }
}
