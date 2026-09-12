package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.fieldvisit.domain.Checklist;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistAnswer;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistItem;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FacilityType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRoute;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypoint;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitRouteWaypointItem;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitRouteDetailResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistAnswerRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldRecordCountRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.RouteWaypointItemRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.RouteWaypointRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteResponseAssemblerTest {

    @Test
    void 공유_경로는_유지하고_호출자_항목_완료_기록만_현재값으로_조립한다() {
        RouteWaypointRepository waypointRepository = mock(RouteWaypointRepository.class);
        RouteWaypointItemRepository linkRepository = mock(RouteWaypointItemRepository.class);
        ChecklistRepository checklistRepository = mock(ChecklistRepository.class);
        ChecklistItemRepository itemRepository = mock(ChecklistItemRepository.class);
        ChecklistAnswerRepository answerRepository = mock(ChecklistAnswerRepository.class);
        FieldRecordCountRepository recordCountRepository = mock(FieldRecordCountRepository.class);
        RouteResponseAssembler assembler = new RouteResponseAssembler(
                waypointRepository, linkRepository, checklistRepository, itemRepository,
                answerRepository, recordCountRepository
        );
        FieldVisitRoute route = mock(FieldVisitRoute.class);
        when(route.getId()).thenReturn(21L);
        when(route.getSessionId()).thenReturn(100L);
        when(route.getGeneratedAt()).thenReturn(Instant.parse("2026-08-02T01:12:00Z"));
        when(route.getTotalDistanceMeters()).thenReturn(500);
        when(route.getEstimatedDurationMinutes()).thenReturn(20);
        JsonNode geometry = JsonNodeFactory.instance.objectNode()
                .put("type", "LineString")
                .set("coordinates", JsonNodeFactory.instance.arrayNode()
                        .add(JsonNodeFactory.instance.arrayNode().add(127.0).add(37.5))
                        .add(JsonNodeFactory.instance.arrayNode().add(127.01).add(37.51)));
        when(route.getGeometry()).thenReturn(geometry);
        Apartment apartment = mock(Apartment.class);
        when(apartment.getId()).thenReturn(3012L);
        when(apartment.getName()).thenReturn("단지");
        when(apartment.getLatitude()).thenReturn(37.5);
        when(apartment.getLongitude()).thenReturn(127.0);
        FieldVisitRouteWaypoint waypoint = waypoint(301L, 1);
        FieldVisitRouteWaypoint duplicatedItemWaypoint = waypoint(302L, 2);
        FieldVisitRouteWaypoint otherOnlyWaypoint = waypoint(303L, 3);
        when(waypointRepository.findByRouteIdOrderBySequenceAsc(21L))
                .thenReturn(List.of(waypoint, duplicatedItemWaypoint, otherOnlyWaypoint));
        FieldVisitRouteWaypointItem ownLink = link(301L, 501L);
        FieldVisitRouteWaypointItem otherLink = link(301L, 502L);
        FieldVisitRouteWaypointItem duplicatedOwnLink = link(302L, 501L);
        FieldVisitRouteWaypointItem otherOnlyLink = link(303L, 502L);
        when(linkRepository.findByWaypointIdIn(List.of(301L, 302L, 303L)))
                .thenReturn(List.of(ownLink, otherLink, duplicatedOwnLink, otherOnlyLink));
        Checklist ownChecklist = mock(Checklist.class);
        when(ownChecklist.getId()).thenReturn(55L);
        when(checklistRepository.findBySessionIdAndMemberId(100L, 1L))
                .thenReturn(Optional.of(ownChecklist));
        ChecklistItem ownItem = item(501L, 55L, 1, "내 항목");
        ChecklistItem otherItem = item(502L, 56L, 1, "다른 항목");
        when(itemRepository.findAllById(anyCollection()))
                .thenReturn(List.of(ownItem, otherItem));
        ChecklistAnswer answer = mock(ChecklistAnswer.class);
        when(answer.getChecklistItemId()).thenReturn(501L);
        when(answer.isCompleted()).thenReturn(true);
        when(answerRepository.findByChecklistItemIdIn(List.of(501L)))
                .thenReturn(List.of(answer));
        when(recordCountRepository.countByAuthorAndItemIds(1L, List.of(501L)))
                .thenReturn(Map.of(501L, 2));

        FieldVisitRouteDetailResponse.RouteResponse response = assembler.toDetailRoute(
                route, apartment, 1L
        );

        assertThat(response.geometry()).isEqualTo(geometry);
        assertThat(response.waypoints()).hasSize(3);
        assertThat(response.waypoints().get(0)).satisfies(point -> {
            assertThat(point.sharedItemCount()).isEqualTo(2);
            assertThat(point.myItems()).singleElement().satisfies(item -> {
                assertThat(item.checklistItemId()).isEqualTo(501L);
                assertThat(item.isCompleted()).isTrue();
                assertThat(item.recordCount()).isEqualTo(2);
            });
        });
        assertThat(response.myProgress().completedWaypointCount()).isEqualTo(3);
        assertThat(response.myProgress().totalWaypointCount()).isEqualTo(3);
        assertThat(response.myProgress().completedItemCount()).isEqualTo(1);
        assertThat(response.myProgress().totalItemCount()).isEqualTo(1);
    }

    private static FieldVisitRouteWaypoint waypoint(Long id, int sequence) {
        FieldVisitRouteWaypoint waypoint = mock(FieldVisitRouteWaypoint.class);
        when(waypoint.getId()).thenReturn(id);
        when(waypoint.getSequence()).thenReturn(sequence);
        when(waypoint.getFacilityType()).thenReturn(FacilityType.MART);
        when(waypoint.getName()).thenReturn("마트 " + sequence);
        when(waypoint.getLatitude()).thenReturn(37.51 + sequence / 1000.0);
        when(waypoint.getLongitude()).thenReturn(127.01);
        when(waypoint.getDistanceFromOriginM()).thenReturn(100 * sequence);
        when(waypoint.getDistanceFromPrevM()).thenReturn(100);
        when(waypoint.getWalkMinutesFromPrev()).thenReturn(2);
        when(waypoint.getStayMinutes()).thenReturn(7);
        return waypoint;
    }

    private static FieldVisitRouteWaypointItem link(Long waypointId, Long itemId) {
        FieldVisitRouteWaypointItem link = mock(FieldVisitRouteWaypointItem.class);
        when(link.getWaypointId()).thenReturn(waypointId);
        when(link.getChecklistItemId()).thenReturn(itemId);
        return link;
    }

    private static ChecklistItem item(
            Long id,
            Long checklistId,
            int order,
            String title
    ) {
        ChecklistItem item = mock(ChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getChecklistId()).thenReturn(checklistId);
        when(item.getDisplayOrder()).thenReturn(order);
        when(item.getCategory()).thenReturn("생활");
        when(item.getTitle()).thenReturn(title);
        return item;
    }
}
