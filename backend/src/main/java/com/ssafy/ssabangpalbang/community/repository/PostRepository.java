package com.ssafy.ssabangpalbang.community.repository;

import com.ssafy.ssabangpalbang.community.domain.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT post FROM Post post WHERE post.id = :postId")
    Optional<Post> findByIdForShare(@Param("postId") Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT post FROM Post post WHERE post.id = :postId")
    Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

    @Query("SELECT post.viewCount FROM Post post WHERE post.id = :postId")
    Optional<Long> findViewCountById(@Param("postId") Long postId);
}
