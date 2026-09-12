package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository
        extends JpaRepository<PostComment, Long> {
}
