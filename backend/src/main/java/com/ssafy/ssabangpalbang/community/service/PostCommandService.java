package com.ssafy.ssabangpalbang.community.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.domain.Post;
import com.ssafy.ssabangpalbang.community.domain.PostAttachment;
import com.ssafy.ssabangpalbang.community.domain.PostStatus;
import com.ssafy.ssabangpalbang.community.dto.request.PostCreateRequest;
import com.ssafy.ssabangpalbang.community.dto.request.PostUpdateRequest;
import com.ssafy.ssabangpalbang.community.dto.response.PostCreateResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostDeleteResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostLikeResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostLikeResult;
import com.ssafy.ssabangpalbang.community.dto.response.PostUnlikeResponse;
import com.ssafy.ssabangpalbang.community.dto.response.PostUnlikeResult;
import com.ssafy.ssabangpalbang.community.dto.response.PostUpdateResponse;
import com.ssafy.ssabangpalbang.community.repository.PostAttachmentRepository;
import com.ssafy.ssabangpalbang.community.repository.PostCommandRepository;
import com.ssafy.ssabangpalbang.community.repository.PostQueryRepository;
import com.ssafy.ssabangpalbang.community.repository.PostRepository;
import com.ssafy.ssabangpalbang.community.repository.projection.PostDetailRow;
import com.ssafy.ssabangpalbang.community.repository.projection.PostInteractionRow;
import com.ssafy.ssabangpalbang.community.response.PostResponseCode;
import com.ssafy.ssabangpalbang.community.support.PostResponseAssembler;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.FileUsage;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.media.service.MediaFileQueryPort;
import com.ssafy.ssabangpalbang.media.service.MediaFileSnapshot;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostCommandService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 5000;
    private static final int MAX_ATTACHMENT_COUNT = 10;
    private static final String ATTACHMENT_FILE_UNIQUE_CONSTRAINT =
            "uq_post_attachment_file";

    private final PostRepository postRepository;
    private final PostAttachmentRepository postAttachmentRepository;
    private final PostCommandRepository postCommandRepository;
    private final PostQueryRepository postQueryRepository;
    private final MemberRepository memberRepository;
    private final ApartmentRepository apartmentRepository;
    private final MediaFileQueryPort mediaFileQueryPort;
    private final PostResponseAssembler postResponseAssembler;
    private final Clock clock;

    @Transactional
    public PostCreateResponse create(
            Long memberId,
            PostCreateRequest request
    ) {
        Member member = findActiveMember(memberId);
        BoardType boardType = parseBoardType(request.boardType());
        String title = normalizeText(
                request.title(),
                "title",
                MAX_TITLE_LENGTH
        );
        String content = normalizeText(
                request.content(),
                "content",
                MAX_CONTENT_LENGTH
        );
        List<Long> fileIds = normalizeAndValidateFileIds(request.fileIds());
        Apartment apartment = findApartment(request.apartmentId());
        List<MediaFileSnapshot> files =
                lockAndValidateFiles(memberId, fileIds);
        validateFilesAreUnused(fileIds);

        Post post = postRepository.save(Post.createMemberPost(
                member.getId(),
                boardType,
                title,
                content,
                apartment == null ? null : apartment.getId()
        ));
        List<PostAttachment> attachments = createAttachments(
                post.getId(),
                fileIds
        );

        flushPostAndAttachments(attachments);

        return postResponseAssembler.assembleCreatedPost(
                post,
                member,
                apartment,
                files
        );
    }

    @Transactional
    public PostUpdateResponse update(
            Long memberId,
            Long postId,
            PostUpdateRequest request
    ) {
        findActiveMember(memberId);
        ValidatedPostUpdate patch = validatePatch(request);
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
        validateEditablePost(post, memberId);

        List<PostAttachment> currentAttachments =
                patch.fileIdsPresent()
                        ? postAttachmentRepository
                        .findAllByPostIdOrderByDisplayOrderAscIdAsc(postId)
                        : List.of();
        if (patch.apartmentIdPresent()
                && patch.apartmentId() != null) {
            findApartment(patch.apartmentId());
        }
        if (patch.fileIdsPresent()) {
            lockAndValidateUpdateFiles(
                    memberId,
                    patch.fileIds(),
                    currentAttachments
            );
            validateFilesAvailableForPost(
                    patch.fileIds(),
                    currentAttachments
            );
        }

        applyPatch(post, patch);
        post.markUpdated(clock.instant());
        flushUpdatedPost(postId, patch);

        return assembleUpdatedPost(memberId, postId);
    }

    @Transactional
    public PostDeleteResponse delete(Long memberId, Long postId) {
        findActiveMember(memberId);
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
        validateDeletablePost(post, memberId);

        post.delete(clock.instant());
        postRepository.flush();

        return PostDeleteResponse.from(
                post.getId(),
                post.getDeletedAt()
        );
    }

    @Transactional
    public PostLikeResult like(Long memberId, Long postId) {
        findActiveMember(memberId);
        Post post = postRepository.findByIdForShare(postId)
                .filter(candidate ->
                        candidate.getStatus() == PostStatus.ACTIVE)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));

        java.util.Optional<Instant> insertedAt =
                postCommandRepository.insertLikeIfAbsent(
                        postId,
                        memberId
                );
        Instant likedAt = insertedAt.orElseGet(() ->
                postCommandRepository.findLikedAt(postId, memberId)
                        .orElseThrow(() -> new IllegalStateException(
                                "좋아요 등록 결과를 확인할 수 없습니다."
                        ))
        );
        PostInteractionRow interactions = postQueryRepository
                .findInteractions(postId, memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));

        return new PostLikeResult(
                insertedAt.isPresent()
                        ? PostResponseCode.POST_LIKE_SUCCESS
                        : PostResponseCode.POST_LIKE_ALREADY_EXISTS,
                PostLikeResponse.from(post, interactions, likedAt)
        );
    }

    @Transactional
    public PostUnlikeResult unlike(Long memberId, Long postId) {
        findActiveMember(memberId);
        Post post = postRepository.findByIdForUpdate(postId)
                .filter(candidate ->
                        candidate.getStatus() == PostStatus.ACTIVE)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));

        int deletedCount = postCommandRepository.deleteLike(
                postId,
                memberId
        );
        Instant unlikedAt = clock.instant();
        PostInteractionRow interactions = postQueryRepository
                .findInteractions(postId, memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));

        return new PostUnlikeResult(
                deletedCount > 0
                        ? PostResponseCode.POST_UNLIKE_SUCCESS
                        : PostResponseCode.POST_ALREADY_UNLIKED,
                PostUnlikeResponse.from(post, interactions, unlikedAt)
        );
    }

    private Member findActiveMember(Long memberId) {
        return memberRepository.findByIdForShare(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private BoardType parseBoardType(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.POST_BOARD_TYPE_INVALID);
        }
        try {
            return BoardType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.POST_BOARD_TYPE_INVALID);
        }
    }

    private BoardType parseUpdateBoardType(String value) {
        if (value == null) {
            throw invalidField(
                    "boardType",
                    "boardType은(는) null일 수 없습니다."
            );
        }
        return parseBoardType(value);
    }

    private String normalizeText(
            String value,
            String field,
            int maxLength
    ) {
        String normalized = value == null
                ? ""
                : stripExtendedWhitespace(value);
        if (normalized.indexOf('\0') >= 0
                || isVisuallyBlank(normalized)
                || normalized.codePointCount(
                        0,
                        normalized.length()
                ) > maxLength) {
            throw invalidField(
                    field,
                    field + "은(는) 공백이 아니며 최대 "
                            + maxLength + "자여야 합니다."
            );
        }
        return normalized;
    }

    private String stripExtendedWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isExtendedWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isExtendedWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private boolean isVisuallyBlank(String value) {
        return value.isEmpty()
                || value.codePoints().allMatch(this::isExtendedWhitespace);
    }

    private boolean isExtendedWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0x200B
                || codePoint == 0xFEFF;
    }

    private List<Long> normalizeAndValidateFileIds(List<Long> requestedIds) {
        if (requestedIds == null) {
            return List.of();
        }
        if (requestedIds.size() > MAX_ATTACHMENT_COUNT) {
            throw new BusinessException(
                    ErrorCode.POST_ATTACHMENT_LIMIT_EXCEEDED
            );
        }

        List<Long> fileIds = new ArrayList<>(requestedIds);
        Set<Long> uniqueIds = new HashSet<>();
        for (Long fileId : fileIds) {
            if (fileId == null || fileId < 1L) {
                throw invalidField(
                        "fileIds",
                        "파일 ID는 1 이상의 숫자여야 합니다."
                );
            }
            if (!uniqueIds.add(fileId)) {
                throw invalidField(
                        "fileIds",
                        "중복된 파일 ID를 사용할 수 없습니다."
                );
            }
        }
        return fileIds;
    }

    private List<Long> validateRequiredFileIds(List<Long> requestedIds) {
        if (requestedIds == null) {
            throw invalidField(
                    "fileIds",
                    "fileIds은(는) null일 수 없습니다."
            );
        }
        return normalizeAndValidateFileIds(requestedIds);
    }

    private Apartment findApartment(Long apartmentId) {
        if (apartmentId == null) {
            return null;
        }
        return apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.APARTMENT_NOT_FOUND,
                        Map.of("apartmentId", apartmentId)
                ));
    }

    private List<MediaFileSnapshot> lockAndValidateFiles(
            Long memberId,
            List<Long> fileIds
    ) {
        if (fileIds.isEmpty()) {
            return List.of();
        }

        List<Long> sortedIds = fileIds.stream().sorted().toList();
        List<MediaFileSnapshot> lockedFiles =
                mediaFileQueryPort.findAllByIdInForUpdate(sortedIds);
        Map<Long, MediaFileSnapshot> fileById = new HashMap<>();
        lockedFiles.forEach(file -> fileById.put(file.fileId(), file));

        for (Long fileId : sortedIds) {
            if (!fileById.containsKey(fileId)) {
                throw new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND,
                        Map.of("fileId", fileId)
                );
            }
        }

        Instant now = clock.instant();
        for (Long fileId : sortedIds) {
            validateFileAccess(
                    memberId,
                    fileById.get(fileId),
                    now
            );
        }

        return fileIds.stream().map(fileById::get).toList();
    }

    private void lockAndValidateUpdateFiles(
            Long memberId,
            List<Long> fileIds,
            List<PostAttachment> currentAttachments
    ) {
        if (fileIds.isEmpty()) {
            return;
        }

        Set<Long> currentFileIds = currentAttachments.stream()
                .map(PostAttachment::getFileId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> sortedIds = fileIds.stream().sorted().toList();
        List<MediaFileSnapshot> lockedFiles =
                mediaFileQueryPort.findAllByIdInForUpdate(sortedIds);
        Map<Long, MediaFileSnapshot> fileById = new HashMap<>();
        lockedFiles.forEach(file -> fileById.put(file.fileId(), file));

        for (Long fileId : sortedIds) {
            if (!fileById.containsKey(fileId)) {
                throw new BusinessException(
                        ErrorCode.MEDIA_FILE_NOT_FOUND,
                        Map.of("fileId", fileId)
                );
            }
        }

        Instant now = clock.instant();
        for (Long fileId : sortedIds) {
            if (!currentFileIds.contains(fileId)) {
                validateFileAccess(
                        memberId,
                        fileById.get(fileId),
                        now
                );
            }
        }
    }

    private void validateFileAccess(
            Long memberId,
            MediaFileSnapshot file,
            Instant now
    ) {
        if (!memberId.equals(file.ownerId())
                || file.fileUsage() != FileUsage.POST_ATTACHMENT
                || file.uploadStatus() != UploadStatus.COMPLETED
                || file.deletedAt() != null
                || isExpired(file, now)) {
            throw new BusinessException(
                    ErrorCode.MEDIA_ACCESS_DENIED,
                    Map.of("fileId", file.fileId())
            );
        }
    }

    private boolean isExpired(MediaFileSnapshot file, Instant now) {
        return file.expiresAt() != null
                && !file.expiresAt().isAfter(now);
    }

    private void validateFilesAreUnused(List<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return;
        }
        Set<Long> attachedIds = new HashSet<>(
                postAttachmentRepository.findAttachedFileIds(fileIds)
        );
        fileIds.stream()
                .filter(attachedIds::contains)
                .findFirst()
                .ifPresent(fileId -> {
                    throw new BusinessException(
                            ErrorCode.POST_ATTACHMENT_ALREADY_USED,
                            Map.of("fileId", fileId)
                    );
                });
    }

    private void validateFilesAvailableForPost(
            List<Long> fileIds,
            List<PostAttachment> currentAttachments
    ) {
        if (fileIds.isEmpty()) {
            return;
        }
        Set<Long> currentFileIds = currentAttachments.stream()
                .map(PostAttachment::getFileId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> attachedIds = new HashSet<>(
                postAttachmentRepository.findAttachedFileIds(fileIds)
        );
        fileIds.stream()
                .filter(attachedIds::contains)
                .filter(fileId -> !currentFileIds.contains(fileId))
                .findFirst()
                .ifPresent(fileId -> {
                    throw new BusinessException(
                            ErrorCode.POST_ATTACHMENT_ALREADY_USED,
                            Map.of("fileId", fileId)
                    );
                });
    }

    private List<PostAttachment> createAttachments(
            Long postId,
            List<Long> fileIds
    ) {
        List<PostAttachment> attachments =
                new ArrayList<>(fileIds.size());
        for (int index = 0; index < fileIds.size(); index++) {
            attachments.add(PostAttachment.create(
                    postId,
                    fileIds.get(index),
                    index
            ));
        }
        return attachments;
    }

    private void flushPostAndAttachments(
            List<PostAttachment> attachments
    ) {
        try {
            if (attachments.isEmpty()) {
                postRepository.flush();
                return;
            }
            postAttachmentRepository.saveAll(attachments);
            postAttachmentRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isAttachmentFileUniqueViolation(exception)) {
                throw new BusinessException(
                        ErrorCode.POST_ATTACHMENT_ALREADY_USED
                );
            }
            throw exception;
        }
    }

    private ValidatedPostUpdate validatePatch(PostUpdateRequest request) {
        if (request == null || !request.hasAnyField()) {
            throw new BusinessException(ErrorCode.POST_UPDATE_EMPTY);
        }

        BoardType boardType = request.isBoardTypePresent()
                ? parseUpdateBoardType(request.boardType())
                : null;
        String title = request.isTitlePresent()
                ? normalizeText(
                        request.title(),
                        "title",
                        MAX_TITLE_LENGTH
                )
                : null;
        String content = request.isContentPresent()
                ? normalizeText(
                        request.content(),
                        "content",
                        MAX_CONTENT_LENGTH
                )
                : null;
        Long apartmentId = request.apartmentId();
        if (request.isApartmentIdPresent()
                && apartmentId != null
                && apartmentId < 1L) {
            throw invalidField(
                    "apartmentId",
                    "아파트 ID는 1 이상의 숫자여야 합니다."
            );
        }
        List<Long> fileIds = request.isFileIdsPresent()
                ? validateRequiredFileIds(request.fileIds())
                : null;

        return new ValidatedPostUpdate(
                request.isBoardTypePresent(),
                boardType,
                request.isTitlePresent(),
                title,
                request.isContentPresent(),
                content,
                request.isApartmentIdPresent(),
                apartmentId,
                request.isFileIdsPresent(),
                fileIds
        );
    }

    private void validateEditablePost(Post post, Long memberId) {
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.isAutoReport()) {
            throw new BusinessException(
                    ErrorCode.POST_AUTO_REPORT_UPDATE_FORBIDDEN
            );
        }
        if (!post.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.POST_UPDATE_FORBIDDEN);
        }
        if (post.isHidden()) {
            throw new BusinessException(
                    ErrorCode.POST_STATUS_CONFLICT,
                    Map.of("status", post.getStatus().name())
            );
        }
    }

    private void validateDeletablePost(Post post, Long memberId) {
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.isAutoReport()) {
            throw new BusinessException(
                    ErrorCode.POST_AUTO_REPORT_DELETE_FORBIDDEN
            );
        }
        if (!post.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.POST_DELETE_FORBIDDEN);
        }
        if (post.isHidden()) {
            throw new BusinessException(
                    ErrorCode.POST_STATUS_CONFLICT,
                    "현재 상태에서는 게시글을 삭제할 수 없습니다.",
                    Map.of("status", post.getStatus().name())
            );
        }
    }

    private void applyPatch(Post post, ValidatedPostUpdate patch) {
        if (patch.boardTypePresent()) {
            post.updateBoardType(patch.boardType());
        }
        if (patch.titlePresent()) {
            post.updateTitle(patch.title());
        }
        if (patch.contentPresent()) {
            post.updateContent(patch.content());
        }
        if (patch.apartmentIdPresent()) {
            post.changeApartment(patch.apartmentId());
        }
    }

    private void flushUpdatedPost(
            Long postId,
            ValidatedPostUpdate patch
    ) {
        try {
            if (!patch.fileIdsPresent()) {
                postRepository.flush();
                return;
            }

            postAttachmentRepository.deleteAllByPostId(postId);
            postAttachmentRepository.flush();
            List<PostAttachment> attachments = createAttachments(
                    postId,
                    patch.fileIds()
            );
            if (!attachments.isEmpty()) {
                postAttachmentRepository.saveAll(attachments);
            }
            postAttachmentRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isAttachmentFileUniqueViolation(exception)) {
                throw new BusinessException(
                        ErrorCode.POST_ATTACHMENT_ALREADY_USED
                );
            }
            throw exception;
        }
    }

    private PostUpdateResponse assembleUpdatedPost(
            Long memberId,
            Long postId
    ) {
        PostDetailRow row = postQueryRepository
                .findVisibleDetail(postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
        PostInteractionRow interactions = postQueryRepository
                .findInteractions(postId, memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
        long viewCount = postRepository.findViewCountById(postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
        List<PostAttachment> attachments = postAttachmentRepository
                .findAllByPostIdOrderByDisplayOrderAscIdAsc(postId);
        List<MediaFileSnapshot> files = mediaFileQueryPort.findAllById(
                attachments.stream()
                        .map(PostAttachment::getFileId)
                        .toList()
        );

        return postResponseAssembler.assembleUpdatedPost(
                row,
                memberId,
                viewCount,
                interactions,
                attachments,
                files
        );
    }

    private boolean isAttachmentFileUniqueViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && ATTACHMENT_FILE_UNIQUE_CONSTRAINT.equals(
                    violation.getConstraintName())) {
                return true;
            }
            if (cause.getMessage() != null
                    && cause.getMessage().contains(
                    ATTACHMENT_FILE_UNIQUE_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private BusinessException invalidField(
            String field,
            String reason
    ) {
        return new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                Map.of("field", field, "reason", reason)
        );
    }

    private record ValidatedPostUpdate(
            boolean boardTypePresent,
            BoardType boardType,
            boolean titlePresent,
            String title,
            boolean contentPresent,
            String content,
            boolean apartmentIdPresent,
            Long apartmentId,
            boolean fileIdsPresent,
            List<Long> fileIds
    ) {
    }
}
