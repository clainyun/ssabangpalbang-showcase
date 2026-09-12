package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyNotice;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeUpdateRequest;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeDeleteResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeListResponse;
import com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeResponse;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyNoticeRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyNoticeService {
    private static final int CONTENT_PREVIEW_MAX_LENGTH = 150;

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final StudyNoticeRepository studyNoticeRepository;
    private final StudyNoticeNotificationWriter notificationWriter;

    @Transactional
    public StudyNoticeResponse createNotice(
            Long memberId, Long studyId, StudyNoticeCreateRequest request
    ) {
        Study study = requireStudy(studyId);
        requireActiveMember(memberId);
        requireLeader(study, memberId, ErrorCode.STUDY_NOTICE_CREATE_FORBIDDEN);
        requireWritable(study, ErrorCode.STUDY_NOTICE_CREATE_NOT_ALLOWED);
        String content = normalizeContent(request.content());
        StudyNotice notice = studyNoticeRepository.save(StudyNotice.create(studyId, content));
        notificationWriter.notifyCreated(study, notice, memberId);
        return StudyNoticeResponse.from(notice);
    }

    public StudyNoticeListResponse getNotices(
            Long memberId, Long studyId, Long cursor, int size
    ) {
        Study study = requireStudy(studyId);
        requireActiveMember(memberId);
        boolean isLeader = StudyAccessPolicy.isLeader(study, memberId);
        boolean isMember = StudyAccessPolicy.isActiveMember(
                studyMemberRepository.findByStudyIdAndStatus(
                        studyId, StudyMemberStatus.ACTIVE), memberId);
        if (!isLeader && !isMember) {
            throw new BusinessException(ErrorCode.STUDY_NOTICE_LIST_FORBIDDEN);
        }
        PageRequest page = PageRequest.of(0, size + 1);
        List<StudyNotice> fetched = cursor == null
                ? studyNoticeRepository.findByStudyIdAndDeletedAtIsNullOrderByIdDesc(
                        studyId, page)
                : studyNoticeRepository
                .findByStudyIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        studyId, cursor, page);
        boolean hasNext = fetched.size() > size;
        List<StudyNotice> pageItems = fetched.stream().limit(size).toList();
        boolean readOnly = StudyAccessPolicy.isReadOnly(study.getStatus());
        boolean canManage = StudyAccessPolicy.canManageNotice(study.getStatus(), isLeader);
        List<StudyNoticeListResponse.NoticeItem> content = pageItems.stream()
                .map(notice -> StudyNoticeListResponse.NoticeItem.from(
                        notice, canManage, preview(notice.getContent())))
                .toList();
        Long nextCursor = hasNext ? pageItems.get(pageItems.size() - 1).getId() : null;
        return new StudyNoticeListResponse(
                studyId, isLeader, readOnly, content, nextCursor, hasNext);
    }

    @Transactional
    public StudyNoticeResponse updateNotice(
            Long memberId, Long studyId, Long noticeId, StudyNoticeUpdateRequest request
    ) {
        Study study = requireStudy(studyId);
        requireActiveMember(memberId);
        requireLeader(study, memberId, ErrorCode.STUDY_NOTICE_UPDATE_FORBIDDEN);
        requireWritable(study, ErrorCode.STUDY_NOTICE_UPDATE_NOT_ALLOWED);
        StudyNotice notice = requireNotice(noticeId);
        requireMatchingStudy(studyId, notice);
        if (request.content() == null) {
            throw new BusinessException(ErrorCode.STUDY_NOTICE_UPDATE_EMPTY);
        }
        notice.updateContent(normalizeContent(request.content()));
        studyNoticeRepository.flush();
        return StudyNoticeResponse.from(notice);
    }

    @Transactional
    public StudyNoticeDeleteResponse deleteNotice(
            Long memberId, Long studyId, Long noticeId
    ) {
        Study study = requireStudy(studyId);
        requireActiveMember(memberId);
        requireLeader(study, memberId, ErrorCode.STUDY_NOTICE_DELETE_FORBIDDEN);
        requireWritable(study, ErrorCode.STUDY_NOTICE_DELETE_NOT_ALLOWED);
        StudyNotice notice = requireNoticeForDelete(noticeId);
        requireMatchingStudy(studyId, notice);
        Instant deletedAt = Instant.now();
        notice.delete(deletedAt);
        studyNoticeRepository.flush();
        return StudyNoticeDeleteResponse.from(studyId, noticeId, deletedAt);
    }

    private Study requireStudy(Long studyId) {
        return studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
    }

    private StudyNotice requireNotice(Long noticeId) {
        return studyNoticeRepository.findByIdAndDeletedAtIsNull(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOTICE_NOT_FOUND));
    }

    private StudyNotice requireNoticeForDelete(Long noticeId) {
        StudyNotice notice = studyNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOTICE_NOT_FOUND));
        if (notice.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.STUDY_NOTICE_ALREADY_DELETED);
        }
        return notice;
    }

    private void requireActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void requireLeader(Study study, Long memberId, ErrorCode errorCode) {
        if (!StudyAccessPolicy.isLeader(study, memberId)) {
            throw new BusinessException(errorCode);
        }
    }

    private void requireWritable(Study study, ErrorCode errorCode) {
        if (StudyAccessPolicy.isReadOnly(study.getStatus())) {
            throw new BusinessException(
                    errorCode,
                    Map.of("studyStatus", study.getStatus().name())
            );
        }
    }

    private void requireMatchingStudy(Long studyId, StudyNotice notice) {
        if (!notice.getStudyId().equals(studyId)) {
            throw new BusinessException(ErrorCode.STUDY_NOTICE_STUDY_MISMATCH);
        }
    }

    private static String preview(String content) {
        return content.length() <= CONTENT_PREVIEW_MAX_LENGTH
                ? content : content.substring(0, CONTENT_PREVIEW_MAX_LENGTH);
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalized = content.strip();
        if (normalized.length() > 2000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }
}
