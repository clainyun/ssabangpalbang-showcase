package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.domain.ApartmentFavorite;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentFavoriteResult;
import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentResponseCode;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentFavoriteRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentTransactionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApartmentFavoriteServiceTest {

    private ApartmentRepository apartmentRepository;
    private ApartmentFavoriteRepository favoriteRepository;
    private MemberRepository memberRepository;
    private ApartmentService service;

    @BeforeEach
    void setUp() {
        apartmentRepository = mock(ApartmentRepository.class);
        favoriteRepository = mock(ApartmentFavoriteRepository.class);
        memberRepository = mock(MemberRepository.class);
        ApartmentTransactionRepository transactionRepository =
                mock(ApartmentTransactionRepository.class);
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));

        service = new ApartmentService(
                apartmentRepository,
                transactionRepository
        );
        ReflectionTestUtils.setField(
                service,
                "apartmentFavoriteRepository",
                favoriteRepository
        );
        ReflectionTestUtils.setField(
                service,
                "memberRepository",
                memberRepository
        );
        ReflectionTestUtils.setField(
                service,
                "transactionManager",
                transactionManager
        );
        stubValidTarget(7L, 15L);
    }

    @Test
    void addsFavorite() {
        when(favoriteRepository.countByApartmentId(15L)).thenReturn(1L);

        ApartmentFavoriteResult result = service.addFavorite(7L, 15L);

        assertThat(result.responseCode())
                .isEqualTo(ApartmentResponseCode.APARTMENT_FAVORITE_SUCCESS);
        assertThat(result.response().favoritedByMe()).isTrue();
        assertThat(result.response().favoriteCount()).isEqualTo(1);
        verify(favoriteRepository).saveAndFlush(any(ApartmentFavorite.class));
    }

    @Test
    void addingTwiceReturnsAlreadyExistsAndKeepsOneRow() {
        when(favoriteRepository.existsByMemberIdAndApartmentId(7L, 15L))
                .thenReturn(false, true);
        when(favoriteRepository.countByApartmentId(15L)).thenReturn(1L);

        ApartmentFavoriteResult first = service.addFavorite(7L, 15L);
        ApartmentFavoriteResult second = service.addFavorite(7L, 15L);

        assertThat(first.responseCode())
                .isEqualTo(ApartmentResponseCode.APARTMENT_FAVORITE_SUCCESS);
        assertThat(second.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_FAVORITE_ALREADY_EXISTS
        );
        assertThat(second.response().favoriteCount()).isEqualTo(1);
        verify(favoriteRepository).saveAndFlush(any(ApartmentFavorite.class));
    }

    @Test
    void duplicateConstraintRaceReturnsAlreadyExists() {
        when(favoriteRepository.saveAndFlush(any(ApartmentFavorite.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(favoriteRepository.countByApartmentId(15L)).thenReturn(1L);

        ApartmentFavoriteResult result = service.addFavorite(7L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_FAVORITE_ALREADY_EXISTS
        );
        assertThat(result.response().favoriteCount()).isEqualTo(1);
    }

    @Test
    void removesFavorite() {
        when(favoriteRepository.deleteByMemberIdAndApartmentId(7L, 15L))
                .thenReturn(1L);

        ApartmentFavoriteResult result = service.removeFavorite(7L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_UNFAVORITE_SUCCESS
        );
        assertThat(result.response().favoritedByMe()).isFalse();
        verify(favoriteRepository).flush();
    }

    @Test
    void removingTwiceReturnsNotFoundOnSecondCall() {
        when(favoriteRepository.deleteByMemberIdAndApartmentId(7L, 15L))
                .thenReturn(1L, 0L);

        ApartmentFavoriteResult first = service.removeFavorite(7L, 15L);
        ApartmentFavoriteResult second = service.removeFavorite(7L, 15L);

        assertThat(first.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_UNFAVORITE_SUCCESS
        );
        assertThat(second.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_FAVORITE_NOT_FOUND
        );
    }

    @Test
    void removingAbsentFavoriteReturnsNotFoundWithZeroCount() {
        when(favoriteRepository.countByApartmentId(15L)).thenReturn(0L);

        ApartmentFavoriteResult result = service.removeFavorite(7L, 15L);

        assertThat(result.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_FAVORITE_NOT_FOUND
        );
        assertThat(result.response().favoriteCount()).isZero();
    }

    @Test
    void favoriteCountChangesFromThreeToTwoAfterRemoval() {
        when(favoriteRepository.deleteByMemberIdAndApartmentId(7L, 15L))
                .thenReturn(1L);
        when(favoriteRepository.countByApartmentId(15L)).thenReturn(2L);

        ApartmentFavoriteResult result = service.removeFavorite(7L, 15L);

        assertThat(result.response().favoriteCount()).isEqualTo(2);
    }

    @Test
    void rejectsMissingApartment() {
        when(apartmentRepository.existsById(15L)).thenReturn(false);

        assertError(
                ErrorCode.APARTMENT_NOT_FOUND,
                () -> service.addFavorite(7L, 15L)
        );
        verify(favoriteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingMember() {
        when(memberRepository.findById(7L)).thenReturn(Optional.empty());

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.addFavorite(7L, 15L)
        );
        verify(apartmentRepository, never()).existsById(any());
    }

    @Test
    void rejectsWithdrawnMember() {
        Member member = activeMember();
        ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.addFavorite(7L, 15L)
        );
    }

    @Test
    void rejectsDeletedMember() {
        Member member = activeMember();
        ReflectionTestUtils.setField(member, "deletedAt", Instant.now());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.addFavorite(7L, 15L)
        );
    }

    private void stubValidTarget(Long memberId, Long apartmentId) {
        when(memberRepository.findById(memberId))
                .thenReturn(Optional.of(activeMember()));
        when(apartmentRepository.existsById(apartmentId)).thenReturn(true);
    }

    private Member activeMember() {
        return new Member("member@example.com", "hash", "member");
    }

    private void assertError(
            ErrorCode expected,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }
}

@DataJpaTest
@ActiveProfiles("test")
@Import(ApartmentService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Sql(statements = {
        "DROP TABLE IF EXISTS apartment_favorite",
        "DROP TABLE IF EXISTS apartment",
        "DROP TABLE IF EXISTS member",
        "CREATE TABLE member (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE, password_hash VARCHAR(255), nickname VARCHAR(50) NOT NULL UNIQUE, profile_image_url VARCHAR(500), selected_character_id VARCHAR(20) NOT NULL, age_group VARCHAR(20), age_group_public_agreed BOOLEAN NOT NULL, service_notification_agreed BOOLEAN NOT NULL, ad_notification_agreed BOOLEAN NOT NULL, status VARCHAR(20) NOT NULL, deleted_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
        "CREATE TABLE apartment (id BIGINT PRIMARY KEY)",
        "CREATE TABLE apartment_favorite (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, member_id BIGINT NOT NULL, apartment_id BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, CONSTRAINT uq_apartment_favorite UNIQUE (member_id, apartment_id))",
        "INSERT INTO member (id, email, nickname, selected_character_id, age_group_public_agreed, service_notification_agreed, ad_notification_agreed, status, created_at, updated_at) VALUES (1, 'one@example.com', 'one', 'PALBANG', FALSE, TRUE, FALSE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "INSERT INTO member (id, email, nickname, selected_character_id, age_group_public_agreed, service_notification_agreed, ad_notification_agreed, status, created_at, updated_at) VALUES (2, 'two@example.com', 'two', 'PALBANG', FALSE, TRUE, FALSE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "INSERT INTO member (id, email, nickname, selected_character_id, age_group_public_agreed, service_notification_agreed, ad_notification_agreed, status, created_at, updated_at) VALUES (3, 'three@example.com', 'three', 'PALBANG', FALSE, TRUE, FALSE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "INSERT INTO apartment (id) VALUES (15)"
})
class ApartmentFavoritePersistenceTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ApartmentService service;

    @org.springframework.beans.factory.annotation.Autowired
    private ApartmentFavoriteRepository favoriteRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void repeatedAddPersistsOnlyOneFavorite() {
        ApartmentFavoriteResult first = service.addFavorite(1L, 15L);
        ApartmentFavoriteResult second = service.addFavorite(1L, 15L);

        assertThat(first.responseCode())
                .isEqualTo(ApartmentResponseCode.APARTMENT_FAVORITE_SUCCESS);
        assertThat(second.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_FAVORITE_ALREADY_EXISTS
        );
        assertThat(favoriteRepository.countByApartmentId(15L)).isEqualTo(1L);
    }

    @Test
    void countChangesFromThreeToTwoAfterFlushAndRepeatedRemoveIsStable() {
        jdbcTemplate.update("""
                INSERT INTO apartment_favorite (
                    member_id, apartment_id, created_at
                ) VALUES (?, ?, CURRENT_TIMESTAMP)
                """, 1L, 15L);
        jdbcTemplate.update("""
                INSERT INTO apartment_favorite (
                    member_id, apartment_id, created_at
                ) VALUES (?, ?, CURRENT_TIMESTAMP)
                """, 2L, 15L);
        jdbcTemplate.update("""
                INSERT INTO apartment_favorite (
                    member_id, apartment_id, created_at
                ) VALUES (?, ?, CURRENT_TIMESTAMP)
                """, 3L, 15L);

        ApartmentFavoriteResult first = service.removeFavorite(1L, 15L);
        ApartmentFavoriteResult second = service.removeFavorite(1L, 15L);

        assertThat(first.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_UNFAVORITE_SUCCESS
        );
        assertThat(first.response().favoriteCount()).isEqualTo(2);
        assertThat(second.responseCode()).isEqualTo(
                ApartmentResponseCode.APARTMENT_FAVORITE_NOT_FOUND
        );
        assertThat(second.response().favoriteCount()).isEqualTo(2);
    }
}
