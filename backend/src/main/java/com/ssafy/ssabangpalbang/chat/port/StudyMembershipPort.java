package com.ssafy.ssabangpalbang.chat.port;

/**
 * 채팅 도메인이 스터디 도메인에 요구하는 최소 권한 조회 계약이다.
 *
 * <p>구현체는 study 패키지에서 제공한다(예: StudyMembershipPortImpl).
 * BE-008이 StudyMember 상태를 올바르게 관리하기만 하면,
 * 이 포트를 사용하는 채팅 쪽 코드는 수정 없이 그대로 연동되는 것을 목표로 한다.</p>
 */
public interface StudyMembershipPort {

    /**
     * 삭제되지 않은 스터디가 존재하고, 해당 회원이 ACTIVE 상태의 StudyMember인지 확인한다.
     * SUBSCRIBE·SEND 권한 검사의 공통 선행 조건이다.
     */
    boolean isStudyMember(Long studyId, Long memberId);

    /**
     * 해당 스터디가 채팅 SEND(TEXT·IMAGE)를 허용하는 상태인지 확인한다.
     *
     * <p>COMPLETED 스터디는 SEND를 차단한다(SUBSCRIBE·이력 조회는 계속 허용).
     * isStudyMember가 true로 확인된 이후에만 호출한다.</p>
     */
    boolean isStudyOpenForSend(Long studyId);
}
