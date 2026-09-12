package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.domain.PostAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostAttachmentRepository
        extends JpaRepository<PostAttachment, Long> {

    @Query("""
            SELECT attachment.fileId
            FROM PostAttachment attachment
            WHERE attachment.fileId IN :fileIds
            """)
    List<Long> findAttachedFileIds(
            @Param("fileIds") Collection<Long> fileIds
    );

    List<PostAttachment> findAllByPostIdOrderByDisplayOrderAscIdAsc(
            Long postId
    );

    List<PostAttachment> findAllByPostIdInOrderByPostIdAscDisplayOrderAscIdAsc(
            Collection<Long> postIds
    );

    @Modifying
    @Query("""
            DELETE FROM PostAttachment attachment
            WHERE attachment.postId = :postId
            """)
    int deleteAllByPostId(@Param("postId") Long postId);
}
