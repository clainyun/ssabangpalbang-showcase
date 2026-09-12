\set ON_ERROR_STOP on

-- FE-021 local demo data for "My Page > AI Report".
-- This file intentionally lives outside db/migration, so Flyway never runs it.
-- Run it explicitly against the local PostgreSQL database with psql -f.
-- The transaction aborts before writing anything unless asdf@asdf.asdf is active.

BEGIN;

DO $$
DECLARE
    v_marker CONSTANT TEXT := '[LOCAL_REPORT_DEMO:FE-021]';
    v_owner_id BIGINT;
    v_participant_2_id BIGINT;
    v_participant_3_id BIGINT;
    v_apartment_id BIGINT;
    v_study_id BIGINT;
    v_session_id BIGINT;
    v_owner_checklist_id BIGINT;
    v_participant_2_checklist_id BIGINT;
    v_participant_3_checklist_id BIGINT;
    v_report_id BIGINT;
    v_marker_study_count INTEGER;
    v_result_json JSONB;
BEGIN
    -- Serialize reruns so the non-schema marker remains unique locally.
    PERFORM pg_advisory_xact_lock(hashtext('LOCAL_REPORT_DEMO:FE-021'));

    SELECT m.id
      INTO v_owner_id
      FROM member m
     WHERE m.email = 'asdf@asdf.asdf'
       AND m.status = 'ACTIVE'
       AND m.deleted_at IS NULL;

    IF v_owner_id IS NULL THEN
        RAISE EXCEPTION
            'FE-021 local report seed requires active member asdf@asdf.asdf';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM member m
         WHERE m.id = v_owner_id
           AND m.password_hash IS NOT NULL
    ) OR NOT EXISTS (
        SELECT 1
          FROM member_preference mp
         WHERE mp.member_id = v_owner_id
    ) THEN
        RAISE EXCEPTION
            'FE-021 target member must support password login and completed onboarding';
    END IF;

    -- These accounts are relationship-only seed participants; they cannot log in.
    IF EXISTS (
        SELECT 1
          FROM member m
         WHERE m.email = 'fe021-report-participant-2@local.invalid'
           AND (
               m.password_hash IS NOT NULL
               OR m.nickname IS DISTINCT FROM 'FE021참여자2'
               OR m.status IS DISTINCT FROM 'ACTIVE'
               OR m.deleted_at IS NOT NULL
           )
    ) OR EXISTS (
        SELECT 1
          FROM member m
         WHERE m.nickname = 'FE021참여자2'
           AND m.email <> 'fe021-report-participant-2@local.invalid'
    ) THEN
        RAISE EXCEPTION 'FE-021 participant 2 identity is owned by non-seed data';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM member m
         WHERE m.email = 'fe021-report-participant-3@local.invalid'
           AND (
               m.password_hash IS NOT NULL
               OR m.nickname IS DISTINCT FROM 'FE021참여자3'
               OR m.status IS DISTINCT FROM 'ACTIVE'
               OR m.deleted_at IS NOT NULL
           )
    ) OR EXISTS (
        SELECT 1
          FROM member m
         WHERE m.nickname = 'FE021참여자3'
           AND m.email <> 'fe021-report-participant-3@local.invalid'
    ) THEN
        RAISE EXCEPTION 'FE-021 participant 3 identity is owned by non-seed data';
    END IF;

    INSERT INTO member (
        email,
        password_hash,
        nickname,
        selected_character_id,
        status,
        deleted_at,
        created_at,
        updated_at
    )
    VALUES (
        'fe021-report-participant-2@local.invalid',
        NULL,
        'FE021참여자2',
        'PALBANG',
        'ACTIVE',
        NULL,
        TIMESTAMPTZ '2026-07-20 00:00:00+00',
        now()
    )
    ON CONFLICT (email) DO UPDATE
       SET updated_at = now()
    RETURNING id INTO v_participant_2_id;

    INSERT INTO member (
        email,
        password_hash,
        nickname,
        selected_character_id,
        status,
        deleted_at,
        created_at,
        updated_at
    )
    VALUES (
        'fe021-report-participant-3@local.invalid',
        NULL,
        'FE021참여자3',
        'PALBANG',
        'ACTIVE',
        NULL,
        TIMESTAMPTZ '2026-07-20 00:00:00+00',
        now()
    )
    ON CONFLICT (email) DO UPDATE
       SET updated_at = now()
    RETURNING id INTO v_participant_3_id;

    IF EXISTS (
        SELECT 1
          FROM apartment a
         WHERE a.complex_code = 'LOCAL-REPORT-DEMO-001'
           AND (
               a.name IS DISTINCT FROM '서울숲 리버뷰 아파트'
               OR a.address IS DISTINCT FROM '서울특별시 성동구 왕십리로 83-21'
               OR a.longitude IS DISTINCT FROM 127.0436
               OR a.latitude IS DISTINCT FROM 37.5447
           )
    ) THEN
        RAISE EXCEPTION
            'FE-021 apartment code LOCAL-REPORT-DEMO-001 is owned by non-seed data';
    END IF;

    INSERT INTO apartment (
        complex_code,
        name,
        address,
        district_code,
        district_name,
        dong_name,
        legal_dong_code,
        longitude,
        latitude,
        household_count,
        completion_year_month,
        parking_space_count,
        created_at,
        updated_at
    )
    VALUES (
        'LOCAL-REPORT-DEMO-001',
        '서울숲 리버뷰 아파트',
        '서울특별시 성동구 왕십리로 83-21',
        '11200',
        '성동구',
        '성수동1가',
        '1120011400',
        127.0436,
        37.5447,
        1234,
        '2018-09',
        1510,
        TIMESTAMPTZ '2026-07-20 00:00:00+00',
        now()
    )
    ON CONFLICT (complex_code) DO UPDATE
       SET name = EXCLUDED.name,
           address = EXCLUDED.address,
           district_code = EXCLUDED.district_code,
           district_name = EXCLUDED.district_name,
           dong_name = EXCLUDED.dong_name,
           legal_dong_code = EXCLUDED.legal_dong_code,
           longitude = EXCLUDED.longitude,
           latitude = EXCLUDED.latitude,
           household_count = EXCLUDED.household_count,
           completion_year_month = EXCLUDED.completion_year_month,
           parking_space_count = EXCLUDED.parking_space_count,
           updated_at = now()
    RETURNING id INTO v_apartment_id;

    SELECT COUNT(*), MIN(s.id)
      INTO v_marker_study_count, v_study_id
      FROM study s
     WHERE s.intro = v_marker;

    IF v_marker_study_count > 1 THEN
        RAISE EXCEPTION
            'FE-021 local report seed marker is duplicated (% rows)',
            v_marker_study_count;
    END IF;

    IF v_study_id IS NOT NULL AND EXISTS (
        SELECT 1
          FROM study s
         WHERE s.id = v_study_id
           AND (
               s.leader_id IS DISTINCT FROM v_owner_id
               OR s.apartment_id IS DISTINCT FROM v_apartment_id
               OR s.title IS DISTINCT FROM '서울숲 리버뷰 아파트 실거주 임장'
           )
    ) THEN
        RAISE EXCEPTION
            'FE-021 study marker is owned by non-seed data';
    END IF;

    IF v_study_id IS NULL THEN
        INSERT INTO study (
            apartment_id,
            leader_id,
            title,
            intro,
            goal,
            capacity,
            purpose,
            status,
            deleted_at,
            canceled_at,
            created_at,
            updated_at
        )
        VALUES (
            v_apartment_id,
            v_owner_id,
            '서울숲 리버뷰 아파트 실거주 임장',
            v_marker,
            '교통, 소음, 주차를 직접 확인하고 실거주 관점의 장단점을 정리한다.',
            3,
            'RESIDENCE',
            'COMPLETED',
            NULL,
            NULL,
            TIMESTAMPTZ '2026-07-20 00:00:00+00',
            now()
        )
        RETURNING id INTO v_study_id;
    ELSE
        UPDATE study
           SET apartment_id = v_apartment_id,
               leader_id = v_owner_id,
               title = '서울숲 리버뷰 아파트 실거주 임장',
               goal = '교통, 소음, 주차를 직접 확인하고 실거주 관점의 장단점을 정리한다.',
               capacity = 3,
               purpose = 'RESIDENCE',
               status = 'COMPLETED',
               deleted_at = NULL,
               canceled_at = NULL,
               updated_at = now()
         WHERE id = v_study_id;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM study_member sm
         WHERE sm.study_id = v_study_id
           AND sm.member_id NOT IN (
               v_owner_id,
               v_participant_2_id,
               v_participant_3_id
           )
    ) THEN
        RAISE EXCEPTION
            'FE-021 seed study has unexpected members';
    END IF;

    INSERT INTO study_member (
        study_id,
        member_id,
        role,
        status,
        joined_at,
        left_at
    )
    VALUES
        (
            v_study_id,
            v_owner_id,
            'LEADER',
            'ACTIVE',
            TIMESTAMPTZ '2026-07-20 00:00:00+00',
            NULL
        ),
        (
            v_study_id,
            v_participant_2_id,
            'MEMBER',
            'ACTIVE',
            TIMESTAMPTZ '2026-07-20 00:01:00+00',
            NULL
        ),
        (
            v_study_id,
            v_participant_3_id,
            'MEMBER',
            'ACTIVE',
            TIMESTAMPTZ '2026-07-20 00:02:00+00',
            NULL
        )
    ON CONFLICT (study_id, member_id) DO UPDATE
       SET role = EXCLUDED.role,
           status = 'ACTIVE',
           joined_at = EXCLUDED.joined_at,
           left_at = NULL;

    INSERT INTO field_session (
        study_id,
        status,
        started_at,
        ended_at,
        ended_by_id,
        end_reason
    )
    VALUES (
        v_study_id,
        'ENDED',
        TIMESTAMPTZ '2026-07-26 01:00:00+00',
        TIMESTAMPTZ '2026-07-26 03:15:00+00',
        v_owner_id,
        'ALL_ENDED'
    )
    ON CONFLICT (study_id) DO UPDATE
       SET status = 'ENDED',
           started_at = EXCLUDED.started_at,
           ended_at = EXCLUDED.ended_at,
           ended_by_id = EXCLUDED.ended_by_id,
           end_reason = EXCLUDED.end_reason
    RETURNING id INTO v_session_id;

    IF EXISTS (
        SELECT 1
          FROM field_participant fp
         WHERE fp.session_id = v_session_id
           AND fp.member_id NOT IN (
               v_owner_id,
               v_participant_2_id,
               v_participant_3_id
           )
    ) THEN
        RAISE EXCEPTION
            'FE-021 seed session has unexpected participants';
    END IF;

    INSERT INTO field_participant (
        session_id,
        member_id,
        status,
        started_at,
        ended_at,
        end_reason,
        stay_duration_sec
    )
    VALUES
        (
            v_session_id,
            v_owner_id,
            'ENDED',
            TIMESTAMPTZ '2026-07-26 01:00:00+00',
            TIMESTAMPTZ '2026-07-26 03:15:00+00',
            'SELF_ENDED',
            8100
        ),
        (
            v_session_id,
            v_participant_2_id,
            'ENDED',
            TIMESTAMPTZ '2026-07-26 01:00:01+00',
            TIMESTAMPTZ '2026-07-26 03:12:00+00',
            'SELF_ENDED',
            7919
        ),
        (
            v_session_id,
            v_participant_3_id,
            'ENDED',
            TIMESTAMPTZ '2026-07-26 01:00:02+00',
            TIMESTAMPTZ '2026-07-26 03:10:00+00',
            'SELF_ENDED',
            7798
        )
    ON CONFLICT (session_id, member_id) DO UPDATE
       SET status = 'ENDED',
           started_at = EXCLUDED.started_at,
           ended_at = EXCLUDED.ended_at,
           end_reason = EXCLUDED.end_reason,
           stay_duration_sec = EXCLUDED.stay_duration_sec;

    IF (
        SELECT COUNT(*)
          FROM field_participant fp
         WHERE fp.session_id = v_session_id
    ) <> 3 THEN
        RAISE EXCEPTION
            'FE-021 seed session participant count is not three';
    END IF;

    INSERT INTO checklist (session_id, member_id, is_fallback, generated_at)
    VALUES (
        v_session_id,
        v_owner_id,
        FALSE,
        TIMESTAMPTZ '2026-07-25 12:00:00+00'
    )
    ON CONFLICT (session_id, member_id) DO UPDATE
       SET is_fallback = FALSE,
           generated_at = EXCLUDED.generated_at
    RETURNING id INTO v_owner_checklist_id;

    INSERT INTO checklist (session_id, member_id, is_fallback, generated_at)
    VALUES (
        v_session_id,
        v_participant_2_id,
        FALSE,
        TIMESTAMPTZ '2026-07-25 12:00:01+00'
    )
    ON CONFLICT (session_id, member_id) DO UPDATE
       SET is_fallback = FALSE,
           generated_at = EXCLUDED.generated_at
    RETURNING id INTO v_participant_2_checklist_id;

    INSERT INTO checklist (session_id, member_id, is_fallback, generated_at)
    VALUES (
        v_session_id,
        v_participant_3_id,
        FALSE,
        TIMESTAMPTZ '2026-07-25 12:00:02+00'
    )
    ON CONFLICT (session_id, member_id) DO UPDATE
       SET is_fallback = FALSE,
           generated_at = EXCLUDED.generated_at
    RETURNING id INTO v_participant_3_checklist_id;

    IF EXISTS (
        SELECT 1
          FROM checklist c
         WHERE c.session_id = v_session_id
           AND c.id NOT IN (
               v_owner_checklist_id,
               v_participant_2_checklist_id,
               v_participant_3_checklist_id
           )
    ) THEN
        RAISE EXCEPTION
            'FE-021 seed session has unexpected checklists';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM checklist_item ci
         WHERE ci.checklist_id IN (
               v_owner_checklist_id,
               v_participant_2_checklist_id,
               v_participant_3_checklist_id
           )
           AND ci.display_order NOT BETWEEN 1 AND 3
    ) THEN
        RAISE EXCEPTION
            'FE-021 seed checklists have unexpected items';
    END IF;

    INSERT INTO checklist_item (
        checklist_id,
        category,
        title,
        subtitle,
        display_order
    )
    SELECT seed.checklist_id,
           seed.category,
           seed.title,
           seed.subtitle,
           seed.display_order
      FROM (VALUES
        (v_owner_checklist_id, '교통', '대중교통 접근성', '역까지 실제 도보 시간', 1),
        (v_owner_checklist_id, '소음', '대로변 소음', '시간대별 창문 개방 소음', 2),
        (v_owner_checklist_id, '주차', '주차 공간', '저녁 시간 주차 여유', 3),
        (v_participant_2_checklist_id, '교통', '출퇴근 동선', '환승과 버스 정류장 접근성', 1),
        (v_participant_2_checklist_id, '소음', '단지 내부 소음', '대로변과 안쪽 동 비교', 2),
        (v_participant_2_checklist_id, '주차', '주차장 혼잡도', '이중 주차 여부', 3),
        (v_participant_3_checklist_id, '교통', '생활권 이동', '주요 상권까지 이동 편의', 1),
        (v_participant_3_checklist_id, '소음', '야간 소음', '안쪽 동 야간 체감 소음', 2),
        (v_participant_3_checklist_id, '주차', '주차 동선', '지하 주차장 진출입 편의', 3)
      ) AS seed(checklist_id, category, title, subtitle, display_order)
    ON CONFLICT (checklist_id, display_order) DO UPDATE
       SET category = EXCLUDED.category,
           title = EXCLUDED.title,
           subtitle = EXCLUDED.subtitle;

    IF (
        SELECT COUNT(*)
          FROM checklist_item ci
         WHERE ci.checklist_id IN (
               v_owner_checklist_id,
               v_participant_2_checklist_id,
               v_participant_3_checklist_id
           )
    ) <> 9 THEN
        RAISE EXCEPTION
            'FE-021 seed checklist item count is not nine';
    END IF;

    INSERT INTO checklist_answer (
        checklist_item_id,
        is_completed,
        completed_at,
        updated_at
    )
    SELECT ci.id,
           NOT (
               ci.checklist_id = v_participant_3_checklist_id
               AND ci.display_order = 3
           ),
           CASE
               WHEN ci.checklist_id = v_participant_3_checklist_id
                    AND ci.display_order = 3
               THEN NULL
               ELSE TIMESTAMPTZ '2026-07-26 03:00:00+00'
           END,
           now()
      FROM checklist_item ci
     WHERE ci.checklist_id IN (
               v_owner_checklist_id,
               v_participant_2_checklist_id,
               v_participant_3_checklist_id
           )
       AND ci.display_order BETWEEN 1 AND 3
    ON CONFLICT (checklist_item_id) DO UPDATE
       SET is_completed = EXCLUDED.is_completed,
           completed_at = EXCLUDED.completed_at,
           updated_at = now();

    IF EXISTS (
        SELECT 1
          FROM field_record fr
         WHERE fr.client_request_id LIKE 'LOCAL-REPORT-DEMO-001:%'
           AND fr.session_id <> v_session_id
    ) THEN
        RAISE EXCEPTION
            'FE-021 field record identifiers are owned by another session';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM field_record fr
         WHERE fr.session_id = v_session_id
           AND fr.deleted_at IS NULL
           AND fr.client_request_id NOT LIKE 'LOCAL-REPORT-DEMO-001:%'
    ) THEN
        RAISE EXCEPTION
            'FE-021 seed session has unexpected field records';
    END IF;

    INSERT INTO field_record (
        session_id,
        checklist_item_id,
        author_id,
        source_type,
        text_content,
        photo_file_id,
        stt_status,
        client_request_id,
        request_fingerprint,
        deleted_at,
        created_at,
        updated_at
    )
    SELECT v_session_id,
           ci.id,
           seed.author_id,
           seed.source_type,
           seed.text_content,
           NULL,
           seed.stt_status,
           seed.client_request_id,
           NULL,
           NULL,
           seed.created_at,
           now()
      FROM (VALUES
        (v_owner_checklist_id, 1, v_owner_id, 'TEXT',
         '서울숲역에서 단지 입구까지 직접 걸어 보니 약 8분이었고 보행 동선도 평탄했다.',
         NULL::TEXT, 'LOCAL-REPORT-DEMO-001:P1:TRANSPORT',
         TIMESTAMPTZ '2026-07-26 01:25:00+00'),
        (v_owner_checklist_id, 2, v_owner_id, 'TEXT',
         '대로변 동은 창문을 열면 차량 소음이 분명하게 들렸고 닫으면 상당히 줄었다.',
         NULL::TEXT, 'LOCAL-REPORT-DEMO-001:P1:NOISE',
         TIMESTAMPTZ '2026-07-26 01:45:00+00'),
        (v_owner_checklist_id, 3, v_owner_id, 'TEXT',
         '지하 2층에는 빈 주차면이 여럿 있어 낮 시간 주차 여유는 괜찮아 보였다.',
         NULL::TEXT, 'LOCAL-REPORT-DEMO-001:P1:PARKING',
         TIMESTAMPTZ '2026-07-26 02:05:00+00'),
        (v_participant_2_checklist_id, 1, v_participant_2_id, 'TEXT',
         '버스 정류장이 단지 앞에 있고 강남 방면 환승 동선도 단순해서 출퇴근이 편리하다.',
         NULL::TEXT, 'LOCAL-REPORT-DEMO-001:P2:TRANSPORT',
         TIMESTAMPTZ '2026-07-26 01:28:00+00'),
        (v_participant_2_checklist_id, 2, v_participant_2_id, 'STT',
         '대로에 가까운 놀이터 쪽은 신호 대기 차량의 소리가 계속 들려 저녁에는 신경 쓰일 수 있다.',
         'DONE', 'LOCAL-REPORT-DEMO-001:P2:NOISE',
         TIMESTAMPTZ '2026-07-26 01:48:00+00'),
        (v_participant_2_checklist_id, 3, v_participant_2_id, 'TEXT',
         '관리실 안내로는 평일 밤 10시 이후 지하 1층이 빠르게 차고 일부 구역은 혼잡하다고 한다.',
         NULL::TEXT, 'LOCAL-REPORT-DEMO-001:P2:PARKING',
         TIMESTAMPTZ '2026-07-26 02:08:00+00'),
        (v_participant_3_checklist_id, 1, v_participant_3_id, 'TEXT',
         '서울숲과 성수 상권을 모두 걸어서 이용할 수 있고 편의점과 병원도 가까웠다.',
         NULL::TEXT, 'LOCAL-REPORT-DEMO-001:P3:TRANSPORT',
         TIMESTAMPTZ '2026-07-26 01:31:00+00'),
        (v_participant_3_checklist_id, 2, v_participant_3_id, 'TEXT',
         '안쪽 동은 대로변과 달리 야간에 창문을 열어도 비교적 조용하게 느껴졌다.',
         NULL::TEXT, 'LOCAL-REPORT-DEMO-001:P3:NOISE',
         TIMESTAMPTZ '2026-07-26 01:51:00+00'),
        (v_participant_3_checklist_id, 3, v_participant_3_id, 'STT',
         '주차장 진입로 폭은 충분하지만 퇴근 시간에는 입구 대기 차량이 생겨 진입이 늦어졌다.',
         'DONE', 'LOCAL-REPORT-DEMO-001:P3:PARKING',
         TIMESTAMPTZ '2026-07-26 02:11:00+00')
      ) AS seed(
          checklist_id,
          display_order,
          author_id,
          source_type,
          text_content,
          stt_status,
          client_request_id,
          created_at
      )
      JOIN checklist_item ci
        ON ci.checklist_id = seed.checklist_id
       AND ci.display_order = seed.display_order
    ON CONFLICT (client_request_id) DO UPDATE
       SET session_id = EXCLUDED.session_id,
           checklist_item_id = EXCLUDED.checklist_item_id,
           author_id = EXCLUDED.author_id,
           source_type = EXCLUDED.source_type,
           text_content = EXCLUDED.text_content,
           photo_file_id = NULL,
           stt_status = EXCLUDED.stt_status,
           request_fingerprint = NULL,
           deleted_at = NULL,
           created_at = EXCLUDED.created_at,
           updated_at = now();

    IF (
        SELECT COUNT(*)
          FROM field_record fr
         WHERE fr.session_id = v_session_id
           AND fr.deleted_at IS NULL
    ) <> 9 THEN
        RAISE EXCEPTION
            'FE-021 seed field record count is not nine';
    END IF;

    v_result_json := $report_json$
    {
      "title": "서울숲 리버뷰 아파트 임장 결산",
      "summary": "세 명이 교통·소음·주차를 직접 확인했습니다. 교통과 생활 편의는 모두 긍정적으로 평가했고, 대로변 소음과 늦은 시간 주차 혼잡은 계약 전에 동·호수별로 다시 확인할 필요가 있습니다.",
      "metrics": {
        "totalChecklistItemCount": 9,
        "completedChecklistItemCount": 8,
        "averageCompletionRate": 88.9,
        "fieldRecordCount": 9
      },
      "topPositiveFeatures": [
        {
          "rank": 1,
          "label": "대중교통 접근성",
          "summary": "지하철역과 버스 정류장을 모두 도보로 이용하기 편리했습니다.",
          "mentionCount": 3,
          "participantRefs": ["P1", "P2", "P3"]
        },
        {
          "rank": 2,
          "label": "생활 편의시설",
          "summary": "서울숲과 성수 상권, 생활 편의시설을 걸어서 이용할 수 있습니다.",
          "mentionCount": 2,
          "participantRefs": ["P1", "P3"]
        },
        {
          "rank": 3,
          "label": "안쪽 동의 정숙성",
          "summary": "단지 안쪽 동은 야간에도 비교적 조용하게 느껴졌습니다.",
          "mentionCount": 1,
          "participantRefs": ["P3"]
        }
      ],
      "topCautionFeatures": [
        {
          "rank": 1,
          "label": "대로변 소음",
          "summary": "대로에 가까운 동은 창문 개방 시 차량 소음이 또렷하게 들렸습니다.",
          "mentionCount": 2,
          "participantRefs": ["P1", "P2"]
        },
        {
          "rank": 2,
          "label": "늦은 시간 주차 혼잡",
          "summary": "밤 10시 이후에는 일부 주차 구역이 빠르게 혼잡해질 수 있습니다.",
          "mentionCount": 1,
          "participantRefs": ["P2"]
        },
        {
          "rank": 3,
          "label": "퇴근 시간 진입 대기",
          "summary": "퇴근 시간에는 주차장 입구에 대기 차량이 생길 수 있습니다.",
          "mentionCount": 1,
          "participantRefs": ["P3"]
        }
      ],
      "commonOpinions": [
        {
          "category": "교통",
          "label": "역과 버스 접근성이 좋음",
          "opinionType": "POSITIVE",
          "summary": "세 참여자 모두 대중교통 동선이 편리하다고 기록했습니다.",
          "participantCount": 3,
          "participantRefs": ["P1", "P2", "P3"]
        },
        {
          "category": "소음",
          "label": "대로변 동은 소음 확인 필요",
          "opinionType": "CAUTION",
          "summary": "두 참여자가 대로변 차량 소음을 주의 요소로 꼽았습니다.",
          "participantCount": 2,
          "participantRefs": ["P1", "P2"]
        }
      ],
      "conflictingOpinions": [
        {
          "category": "주차",
          "label": "시간대에 따라 다른 주차 여유",
          "summary": "낮에는 여유가 있었지만 늦은 시간 혼잡 가능성에 대한 의견이 갈렸습니다.",
          "positiveParticipantCount": 1,
          "cautionParticipantCount": 2,
          "positiveParticipantRefs": ["P1"],
          "cautionParticipantRefs": ["P2", "P3"]
        }
      ],
      "categories": [
        {
          "category": "교통",
          "summary": "세 참여자 모두 지하철·버스·생활권 이동을 긍정적으로 평가했습니다.",
          "positiveOpinionCount": 3,
          "cautionOpinionCount": 0,
          "dataSufficient": true,
          "participantOpinions": [
            {"participantRef": "P1", "participantLabel": "참여자 1", "opinionType": "POSITIVE", "summary": "역까지 약 8분이고 보행 동선이 평탄했습니다."},
            {"participantRef": "P2", "participantLabel": "참여자 2", "opinionType": "POSITIVE", "summary": "단지 앞 버스 정류장과 환승 동선이 편리했습니다."},
            {"participantRef": "P3", "participantLabel": "참여자 3", "opinionType": "POSITIVE", "summary": "서울숲과 성수 상권을 도보로 이용할 수 있었습니다."}
          ]
        },
        {
          "category": "소음",
          "summary": "대로변과 안쪽 동의 체감 소음 차이가 뚜렷했습니다.",
          "positiveOpinionCount": 1,
          "cautionOpinionCount": 2,
          "dataSufficient": true,
          "participantOpinions": [
            {"participantRef": "P1", "participantLabel": "참여자 1", "opinionType": "CAUTION", "summary": "대로변 동은 창문을 열면 차량 소음이 들렸습니다."},
            {"participantRef": "P2", "participantLabel": "참여자 2", "opinionType": "CAUTION", "summary": "놀이터 쪽에서도 신호 대기 차량 소리가 이어졌습니다."},
            {"participantRef": "P3", "participantLabel": "참여자 3", "opinionType": "POSITIVE", "summary": "안쪽 동은 야간에도 비교적 조용했습니다."}
          ]
        },
        {
          "category": "주차",
          "summary": "낮에는 여유가 있었지만 퇴근 이후 혼잡 가능성을 함께 확인했습니다.",
          "positiveOpinionCount": 1,
          "cautionOpinionCount": 2,
          "dataSufficient": true,
          "participantOpinions": [
            {"participantRef": "P1", "participantLabel": "참여자 1", "opinionType": "POSITIVE", "summary": "낮 시간 지하 2층에는 빈 주차면이 있었습니다."},
            {"participantRef": "P2", "participantLabel": "참여자 2", "opinionType": "CAUTION", "summary": "밤 10시 이후 일부 구역이 혼잡하다는 안내를 받았습니다."},
            {"participantRef": "P3", "participantLabel": "참여자 3", "opinionType": "CAUTION", "summary": "퇴근 시간에는 입구 진입 대기가 있었습니다."}
          ]
        }
      ]
    }
    $report_json$::JSONB;

    IF EXISTS (
        SELECT 1
          FROM report r
         WHERE r.study_id = v_study_id
           AND (
               r.apartment_id IS DISTINCT FROM v_apartment_id
               OR r.field_session_id IS DISTINCT FROM v_session_id
           )
    ) THEN
        RAISE EXCEPTION
            'FE-021 seed study report is owned by non-seed data';
    END IF;

    INSERT INTO report (
        study_id,
        apartment_id,
        status,
        progress_stage,
        result_json,
        fail_reason,
        is_retryable,
        published_at,
        completed_at,
        created_at,
        updated_at,
        field_session_id,
        processing_token_hash,
        processing_attempt,
        processing_lease_expires_at,
        complete_payload_hash,
        fail_code,
        failed_at,
        fail_payload_hash
    )
    VALUES (
        v_study_id,
        v_apartment_id,
        'DONE',
        'COMPLETED',
        v_result_json,
        NULL,
        FALSE,
        TIMESTAMPTZ '2026-07-26 03:20:00+00',
        TIMESTAMPTZ '2026-07-26 03:20:00+00',
        TIMESTAMPTZ '2026-07-26 03:16:00+00',
        now(),
        v_session_id,
        NULL,
        1,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL
    )
    ON CONFLICT (study_id) DO UPDATE
       SET apartment_id = EXCLUDED.apartment_id,
           status = 'DONE',
           progress_stage = 'COMPLETED',
           result_json = EXCLUDED.result_json,
           fail_reason = NULL,
           is_retryable = FALSE,
           published_at = EXCLUDED.published_at,
           completed_at = EXCLUDED.completed_at,
           updated_at = now(),
           field_session_id = EXCLUDED.field_session_id,
           processing_token_hash = NULL,
           processing_attempt = 1,
           processing_lease_expires_at = NULL,
           complete_payload_hash = NULL,
           fail_code = NULL,
           failed_at = NULL,
           fail_payload_hash = NULL
    RETURNING id INTO v_report_id;

    -- The result's claim order is features, common/conflict, then category opinions.
    -- Rebuilding only this seed report's links makes reruns deterministic.
    DELETE FROM report_evidence
     WHERE report_id = v_report_id;

    INSERT INTO report_evidence (
        report_id,
        field_record_id,
        claim_key,
        display_order
    )
    SELECT v_report_id,
           fr.id,
           link.claim_key,
           link.display_order
      FROM (VALUES
        ('LOCAL-REPORT-DEMO-001:P1:TRANSPORT', 'positive-transport', 1),
        ('LOCAL-REPORT-DEMO-001:P2:TRANSPORT', 'positive-transport', 1),
        ('LOCAL-REPORT-DEMO-001:P3:TRANSPORT', 'positive-transport', 1),
        ('LOCAL-REPORT-DEMO-001:P3:TRANSPORT', 'positive-lifestyle', 2),
        ('LOCAL-REPORT-DEMO-001:P3:NOISE', 'positive-quiet-inside', 3),
        ('LOCAL-REPORT-DEMO-001:P1:NOISE', 'caution-noise', 4),
        ('LOCAL-REPORT-DEMO-001:P2:NOISE', 'caution-noise', 4),
        ('LOCAL-REPORT-DEMO-001:P2:PARKING', 'caution-late-parking', 5),
        ('LOCAL-REPORT-DEMO-001:P3:PARKING', 'caution-entry-wait', 6),
        ('LOCAL-REPORT-DEMO-001:P1:TRANSPORT', 'common-transport', 7),
        ('LOCAL-REPORT-DEMO-001:P2:TRANSPORT', 'common-transport', 7),
        ('LOCAL-REPORT-DEMO-001:P3:TRANSPORT', 'common-transport', 7),
        ('LOCAL-REPORT-DEMO-001:P1:NOISE', 'common-noise', 8),
        ('LOCAL-REPORT-DEMO-001:P2:NOISE', 'common-noise', 8),
        ('LOCAL-REPORT-DEMO-001:P1:PARKING', 'conflict-parking', 9),
        ('LOCAL-REPORT-DEMO-001:P2:PARKING', 'conflict-parking', 9),
        ('LOCAL-REPORT-DEMO-001:P3:PARKING', 'conflict-parking', 9),
        ('LOCAL-REPORT-DEMO-001:P1:TRANSPORT', 'category-transport-p1', 10),
        ('LOCAL-REPORT-DEMO-001:P2:TRANSPORT', 'category-transport-p2', 11),
        ('LOCAL-REPORT-DEMO-001:P3:TRANSPORT', 'category-transport-p3', 12),
        ('LOCAL-REPORT-DEMO-001:P1:NOISE', 'category-noise-p1', 13),
        ('LOCAL-REPORT-DEMO-001:P2:NOISE', 'category-noise-p2', 14),
        ('LOCAL-REPORT-DEMO-001:P3:NOISE', 'category-noise-p3', 15),
        ('LOCAL-REPORT-DEMO-001:P1:PARKING', 'category-parking-p1', 16),
        ('LOCAL-REPORT-DEMO-001:P2:PARKING', 'category-parking-p2', 17),
        ('LOCAL-REPORT-DEMO-001:P3:PARKING', 'category-parking-p3', 18)
      ) AS link(client_request_id, claim_key, display_order)
      JOIN field_record fr
        ON fr.client_request_id = link.client_request_id;

    IF (SELECT COUNT(*) FROM report_evidence re
         WHERE re.report_id = v_report_id) <> 26
       OR (SELECT COUNT(DISTINCT re.display_order)
             FROM report_evidence re
            WHERE re.report_id = v_report_id) <> 18
       OR (SELECT MIN(re.display_order)
             FROM report_evidence re
            WHERE re.report_id = v_report_id) <> 1
       OR (SELECT MAX(re.display_order)
             FROM report_evidence re
            WHERE re.report_id = v_report_id) <> 18 THEN
        RAISE EXCEPTION 'FE-021 report evidence rebuild was incomplete';
    END IF;
END
$$;

COMMIT;
