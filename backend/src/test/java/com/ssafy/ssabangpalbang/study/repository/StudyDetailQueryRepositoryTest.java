package com.ssafy.ssabangpalbang.study.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyDetailQueryRepositoryTest {

    @Mock EntityManager entityManager;
    @Mock Query query;

    @Test
    void 상세_읽지_않음_쿼리는_본인_메시지를_제외한다() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        StudyDetailQueryRepository repository = new StudyDetailQueryRepository(entityManager);

        repository.countUnreadChat(7L, 42L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains("cm.sender_id IS DISTINCT FROM :memberId");
        verify(query).setParameter("memberId", 42L);
    }
}
