package com.ssafy.ssabangpalbang.quality;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

@Tag("postgres")
@Testcontainers
class Be033ErdV7ConstraintPostgresTest {

    private static final Set<String> EXPECTED_FOREIGN_KEYS = Set.of(
            "member_preference(member_id)->member(id)",
            "social_account(member_id)->member(id)",
            "fcm_token(member_id)->member(id)",
            "follow(follower_id)->member(id)",
            "follow(following_id)->member(id)",
            "notification(recipient_id)->member(id)",
            "notification(actor_id)->member(id)",
            "apartment_transaction(apartment_id)->apartment(id)",
            "apartment_favorite(member_id)->member(id)",
            "apartment_favorite(apartment_id)->apartment(id)",
            "study(apartment_id)->apartment(id)",
            "study(leader_id)->member(id)",
            "study_notice(study_id)->study(id)",
            "study_application(study_id)->study(id)",
            "study_application(applicant_id)->member(id)",
            "study_member(study_id)->study(id)",
            "study_member(member_id)->member(id)",
            "schedule(study_id)->study(id)",
            "file_meta(owner_id)->member(id)",
            "file_meta(study_id)->study(id)",
            "chat_message(study_id)->study(id)",
            "chat_message(sender_id)->member(id)",
            "chat_message(image_file_id)->file_meta(id)",
            "chat_read_status(study_id)->study(id)",
            "chat_read_status(member_id)->member(id)",
            "field_session(study_id)->study(id)",
            "field_session(ended_by_id)->member(id)",
            "field_participant(session_id)->field_session(id)",
            "field_participant(member_id)->member(id)",
            "checklist(session_id)->field_session(id)",
            "checklist(member_id)->member(id)",
            "checklist_item(checklist_id)->checklist(id)",
            "field_record(session_id)->field_session(id)",
            "field_record(checklist_item_id)->checklist_item(id)",
            "field_record(author_id)->member(id)",
            "field_record(photo_file_id)->file_meta(id)",
            "checklist_answer(checklist_item_id)->checklist_item(id)",
            "report(study_id)->study(id)",
            "report(apartment_id)->apartment(id)",
            "report(field_session_id)->field_session(id)",
            "report_evidence(report_id)->report(id)",
            "report_evidence(field_record_id)->field_record(id)",
            "report_favorite(member_id)->member(id)",
            "report_favorite(report_id)->report(id)",
            "post(author_id)->member(id)",
            "post(report_id)->report(id)",
            "post(apartment_id)->apartment(id)",
            "post_comment(post_id)->post(id)",
            "post_comment(author_id)->member(id)",
            "post_like(post_id)->post(id)",
            "post_like(member_id)->member(id)",
            "post_attachment(post_id)->post(id)",
            "post_attachment(file_id)->file_meta(id)",
            "chatbot_conversation(member_id)->member(id)",
            "chatbot_conversation(apartment_id)->apartment(id)",
            "chatbot_message(conversation_id)->chatbot_conversation(id)",
            "apartment_rag_document(apartment_id)->apartment(id)",
            "stt_job(member_id)->member(id)",
            "stt_job(study_id)->study(id)",
            "stt_job(session_id)->field_session(id)",
            "stt_job(audio_file_id)->file_meta(id)",
            "stt_job(checklist_item_id)->checklist_item(id)",
            "stt_job(field_record_id)->field_record(id)",
            "stt_job_attempt(stt_job_id)->stt_job(id)",
            "stt_job_attempt(member_id)->member(id)",
            "field_visit_start_request(member_id)->member(id)",
            "field_visit_start_request(study_id)->study(id)",
            "field_visit_start_request(session_id)->field_session(id)",
            "field_visit_start_request(participant_id)->field_participant(id)",
            "field_visit_candidate(session_id)->field_session(id)",
            "field_visit_candidate(member_id)->member(id)",
            "stt_dispatch_outbox(stt_job_id)->stt_job(id)",
            "stt_dispatch_outbox(audio_file_id)->file_meta(id)",
            "stt_audio_cleanup_job(audio_file_id)->file_meta(id)",
            "field_visit_route(session_id)->field_session(id)",
            "field_visit_route(generated_by_id)->member(id)",
            "field_visit_route_waypoint(route_id)->field_visit_route(id)",
            "field_visit_route_waypoint_item(waypoint_id)->field_visit_route_waypoint(id)",
            "field_visit_route_waypoint_item(checklist_item_id)->checklist_item(id)",
            "apartment_image(apartment_id)->apartment(id)",
            "field_visit_close_vote(field_session_id)->field_session(id)",
            "field_visit_close_vote(field_participant_id)->field_participant(id)",
            "member_review(study_id)->study(id)",
            "member_review(reviewer_id)->member(id)",
            "member_review(reviewee_id)->member(id)",
            "checklist_generation_progress(session_id)->field_session(id)",
            "checklist_generation_progress(member_id)->member(id)"
    );

    private static final Set<String> EXPECTED_CASCADE_FOREIGN_KEYS = Set.of(
            "field_visit_route_waypoint(route_id)->field_visit_route(id)",
            "field_visit_route_waypoint_item(waypoint_id)->field_visit_route_waypoint(id)",
            "field_visit_route_waypoint_item(checklist_item_id)->checklist_item(id)"
    );

    private static final Set<String> EXPECTED_UNIQUES = Set.of(
            "member(email)", "member(nickname)",
            "member_preference(member_id)",
            "social_account(provider,social_user_id)",
            "fcm_token(token)", "fcm_token(member_id,device_id)",
            "follow(follower_id,following_id)",
            "notification(idempotency_key)",
            "apartment(complex_code)",
            "apartment_transaction(dedup_key)",
            "apartment_favorite(member_id,apartment_id)",
            "study_application(study_id,applicant_id)",
            "study_member(study_id,member_id)",
            "schedule(study_id)",
            "file_meta(s3_key)",
            "chat_read_status(study_id,member_id)",
            "field_session(study_id)",
            "field_participant(session_id,member_id)",
            "checklist(session_id,member_id)",
            "checklist_item(checklist_id,display_order)",
            "field_record(client_request_id)",
            "checklist_answer(checklist_item_id)",
            "report(study_id)",
            "report(field_session_id)",
            "report_evidence(report_id,field_record_id,claim_key)",
            "report_favorite(member_id,report_id)",
            "post_like(post_id,member_id)",
            "post_attachment(post_id,file_id)",
            "post_attachment(post_id,display_order)",
            "apartment_rag_document(reindex_key)",
            "stt_job(stt_id)",
            "stt_job(audio_file_id)",
            "stt_job(member_id,initial_client_request_id)",
            "stt_job(field_record_id)",
            "stt_job_attempt(member_id,client_request_id)",
            "stt_job_attempt(stt_job_id,attempt_no)",
            "field_visit_start_request(member_id,client_request_id)",
            "field_visit_candidate(session_id,member_id)",
            "stt_dispatch_outbox(stt_id,attempt_no)",
            "stt_audio_cleanup_job(audio_file_id)",
            "field_visit_route(session_id)",
            "field_visit_route_waypoint(route_id,sequence)",
            "field_visit_route_waypoint_item(waypoint_id,checklist_item_id)",
            "apartment_image(object_key)",
            "field_visit_close_vote(field_session_id,field_participant_id)",
            "member_review(study_id,reviewer_id,reviewee_id)",
            "checklist_generation_progress(session_id,member_id,attempt_id)"
    );

    private static final Set<String> EXPECTED_CHECKS = Set.of(
            "follow.follow_check",
            "apartment.apartment_household_count_check",
            "apartment.apartment_parking_space_count_check",
            "apartment.apartment_completion_year_month_check",
            "study.study_capacity_check",
            "study.study_goal_check",
            "schedule.schedule_check",
            "file_meta.file_meta_size_bytes_check",
            "field_participant.field_participant_stay_duration_sec_check",
            "checklist_item.checklist_item_title_check",
            "post.post_view_count_check",
            "post.post_check",
            "post.ck_post_board_type",
            "post.ck_post_status",
            "post.ck_post_title_not_blank",
            "post.ck_post_content_length",
            "post.ck_post_origin",
            "chatbot_message.chatbot_message_role_check",
            "chatbot_message.chatbot_message_status_check",
            "chatbot_message.chatbot_message_check",
            "chatbot_message.chatbot_message_check1",
            "stt_job.ck_stt_job_status",
            "stt_job.ck_stt_job_retry_count",
            "stt_job.ck_stt_job_dispatch_count",
            "stt_job_attempt.ck_stt_job_attempt_no",
            "stt_job_attempt.ck_stt_job_attempt_request_type",
            "stt_job_attempt.ck_stt_job_attempt_status",
            "stt_dispatch_outbox.ck_stt_dispatch_outbox_attempt_no",
            "stt_dispatch_outbox.ck_stt_dispatch_outbox_attempt_count",
            "stt_dispatch_outbox.ck_stt_dispatch_outbox_status",
            "stt_audio_cleanup_job.ck_stt_audio_cleanup_attempt_count",
            "stt_audio_cleanup_job.ck_stt_audio_cleanup_status",
            "report.ck_report_processing_attempt",
            "report.ck_report_processing_token_hash",
            "report.ck_report_complete_payload_hash",
            "report.ck_report_fail_code",
            "report.ck_report_fail_payload_hash",
            "field_visit_route.ck_route_distance",
            "field_visit_route.ck_route_duration",
            "field_visit_route_waypoint.ck_route_waypoint_sequence",
            "field_visit_route_waypoint.ck_route_waypoint_name",
            "field_visit_route_waypoint.ck_route_waypoint_lat",
            "field_visit_route_waypoint.ck_route_waypoint_lng",
            "apartment_image.chk_apartment_image_sha256",
            "apartment_image.chk_apartment_image_match_confidence",
            "member_review.ck_member_review_rating",
            "member_review.ck_member_review_not_self",
            "checklist_generation_progress.checklist_generation_progress_status_check",
            "checklist_generation_progress.checklist_generation_progress_progress_rate_check"
    );

    private static final Set<String> EXPECTED_INDEXES = Set.of(
            "idx_apartment_district", "idx_apartment_dong", "idx_apartment_coord",
            "idx_apartment_name_trgm", "idx_apartment_address_trgm",
            "idx_apartment_dong_trgm", "idx_apartment_location_gist",
            "idx_transaction_recent", "idx_apartment_favorite_member",
            "idx_study_apartment_status", "idx_study_member_member_status",
            "idx_schedule_start", "idx_file_meta_expiry", "idx_chat_message_study",
            "idx_notification_recipient", "idx_notification_target",
            "idx_field_participant_member", "idx_field_record_author",
            "idx_field_record_item", "idx_report_apartment_status",
            "idx_report_favorite_member", "idx_report_evidence_report",
            "idx_post_board_recent", "idx_post_hot_candidates", "idx_post_author",
            "uq_post_auto_report_per_report", "idx_post_comment_post",
            "idx_post_comment_author", "idx_post_like_post",
            "idx_post_attachment_post", "idx_post_search_trgm", "idx_follow_follower",
            "idx_chatbot_conversation_member", "idx_chatbot_message_conv",
            "idx_chatbot_message_processing", "uq_chat_message_sender_client_message",
            "idx_stt_job_member_status", "idx_stt_job_study_status",
            "idx_stt_job_attempt_job", "uq_post_attachment_file",
            "idx_study_notice_active_study_id_id", "idx_field_visit_start_request_session",
            "idx_post_comment_post_created_active", "idx_stt_dispatch_outbox_ready",
            "idx_stt_audio_cleanup_ready", "idx_route_waypoint_item_checklist_item",
            "idx_field_visit_close_vote_session_id", "idx_notification_recipient_read_sent",
            "idx_member_review_reviewee_created"
    );

    private static final Map<String, List<String>> EXPECTED_CHECK_FRAGMENTS = Map.ofEntries(
            Map.entry("follow.follow_check", List.of("follower_id", "following_id", "<>")),
            Map.entry("apartment.apartment_household_count_check", List.of("household_count", ">= 0")),
            Map.entry("apartment.apartment_parking_space_count_check", List.of("parking_space_count", ">= 0")),
            Map.entry("apartment.apartment_completion_year_month_check", List.of("completion_year_month", "^[0-9]{4}-")),
            Map.entry("study.study_capacity_check", List.of("capacity", "> 0")),
            Map.entry("study.study_goal_check", List.of("btrim", "goal", "<>")),
            Map.entry("schedule.schedule_check", List.of("end_at", "start_at", ">=")),
            Map.entry("file_meta.file_meta_size_bytes_check", List.of("size_bytes", ">= 0")),
            Map.entry("field_participant.field_participant_stay_duration_sec_check", List.of("stay_duration_sec", ">= 0")),
            Map.entry("checklist_item.checklist_item_title_check", List.of("btrim", "title", "<>")),
            Map.entry("post.post_view_count_check", List.of("view_count", ">= 0")),
            Map.entry("post.post_check", List.of("is_auto_report", "report_id", "information")),
            Map.entry("post.ck_post_board_type", List.of("board_type", "information", "free")),
            Map.entry("post.ck_post_status", List.of("status", "active", "hidden")),
            Map.entry("post.ck_post_title_not_blank", List.of("char_length", "btrim", "title", ">= 1", "<= 200")),
            Map.entry("post.ck_post_content_length", List.of("content is not null", "char_length", "btrim", ">= 1", "<= 5000")),
            Map.entry("post.ck_post_origin", List.of("is_auto_report", "author_id", "report_id", "information")),
            Map.entry("chatbot_message.chatbot_message_role_check", List.of("role", "user", "assistant")),
            Map.entry("chatbot_message.chatbot_message_status_check", List.of("status", "pending", "processing", "completed", "failed")),
            Map.entry("chatbot_message.chatbot_message_check", List.of("role", "status", "content", "assistant")),
            Map.entry("chatbot_message.chatbot_message_check1", List.of("status", "failed", "fail_reason")),
            Map.entry("stt_job.ck_stt_job_status", List.of("status", "pending", "processing", "done", "failed")),
            Map.entry("stt_job.ck_stt_job_retry_count", List.of("retry_count", ">= 0")),
            Map.entry("stt_job.ck_stt_job_dispatch_count", List.of("dispatch_count", ">= 0")),
            Map.entry("stt_job_attempt.ck_stt_job_attempt_no", List.of("attempt_no", ">= 1")),
            Map.entry("stt_job_attempt.ck_stt_job_attempt_request_type", List.of("request_type", "initial", "retry")),
            Map.entry("stt_job_attempt.ck_stt_job_attempt_status", List.of("status", "pending", "processing", "done", "failed")),
            Map.entry("stt_dispatch_outbox.ck_stt_dispatch_outbox_attempt_no", List.of("attempt_no", ">= 1")),
            Map.entry("stt_dispatch_outbox.ck_stt_dispatch_outbox_attempt_count", List.of("attempt_count", ">= 0")),
            Map.entry("stt_dispatch_outbox.ck_stt_dispatch_outbox_status", List.of("status", "pending", "published", "failed")),
            Map.entry("stt_audio_cleanup_job.ck_stt_audio_cleanup_attempt_count", List.of("attempt_count", ">= 0")),
            Map.entry("stt_audio_cleanup_job.ck_stt_audio_cleanup_status", List.of("status", "pending", "completed", "failed")),
            Map.entry("report.ck_report_processing_attempt", List.of("processing_attempt", ">= 0")),
            Map.entry("report.ck_report_processing_token_hash", List.of("processing_token_hash", "^[0-9a-f]{64}$")),
            Map.entry("report.ck_report_complete_payload_hash", List.of("complete_payload_hash", "^[0-9a-f]{64}$")),
            Map.entry("report.ck_report_fail_code", List.of("fail_code", "^[a-z][a-z0-9_]{0,99}$")),
            Map.entry("report.ck_report_fail_payload_hash", List.of("fail_payload_hash", "^[0-9a-f]{64}$")),
            Map.entry("field_visit_route.ck_route_distance", List.of("total_distance_meters", ">= 0")),
            Map.entry("field_visit_route.ck_route_duration", List.of("estimated_duration_minutes", ">= 0")),
            Map.entry("field_visit_route_waypoint.ck_route_waypoint_sequence", List.of("sequence", ">= 1")),
            Map.entry("field_visit_route_waypoint.ck_route_waypoint_name", List.of("btrim", "name", "<>")),
            Map.entry("field_visit_route_waypoint.ck_route_waypoint_lat", List.of("latitude", "-90", "90")),
            Map.entry("field_visit_route_waypoint.ck_route_waypoint_lng", List.of("longitude", "-180", "180")),
            Map.entry("apartment_image.chk_apartment_image_sha256", List.of("sha256", "^[0-9a-f]{64}$")),
            Map.entry("apartment_image.chk_apartment_image_match_confidence", List.of("match_confidence", "high", "medium", "low")),
            Map.entry("member_review.ck_member_review_rating", List.of("rating", "1", "5")),
            Map.entry("member_review.ck_member_review_not_self", List.of("reviewer_id", "reviewee_id", "<>")),
            Map.entry("checklist_generation_progress.checklist_generation_progress_status_check", List.of("status", "in_progress", "done", "failed")),
            Map.entry("checklist_generation_progress.checklist_generation_progress_progress_rate_check", List.of("progress_rate", "0", "100"))
    );

    private static final Map<String, IndexExpectation> EXPECTED_INDEX_DEFINITIONS = Map.ofEntries(
            Map.entry("idx_apartment_district", index("apartment", false, "btree", List.of("district_code", "district_name"), List.of())),
            Map.entry("idx_apartment_dong", index("apartment", false, "btree", List.of("district_code", "dong_name"), List.of())),
            Map.entry("idx_apartment_coord", index("apartment", false, "btree", List.of("latitude", "longitude"), List.of())),
            Map.entry("idx_apartment_name_trgm", index("apartment", false, "gin", List.of("name", "gin_trgm_ops"), List.of())),
            Map.entry("idx_apartment_address_trgm", index("apartment", false, "gin", List.of("address", "gin_trgm_ops"), List.of())),
            Map.entry("idx_apartment_dong_trgm", index("apartment", false, "gin", List.of("dong_name", "gin_trgm_ops"), List.of())),
            Map.entry("idx_apartment_location_gist", index("apartment", false, "gist", List.of("st_setsrid", "st_makepoint", "longitude", "latitude", "geography"), List.of())),
            Map.entry("idx_transaction_recent", index("apartment_transaction", false, "btree", List.of("apartment_id", "deal_date desc"), List.of())),
            Map.entry("idx_apartment_favorite_member", index("apartment_favorite", false, "btree", List.of("member_id", "id desc"), List.of())),
            Map.entry("idx_study_apartment_status", index("study", false, "btree", List.of("apartment_id", "status", "id desc"), List.of())),
            Map.entry("idx_study_member_member_status", index("study_member", false, "btree", List.of("member_id", "status", "study_id"), List.of())),
            Map.entry("idx_schedule_start", index("schedule", false, "btree", List.of("start_at", "status"), List.of())),
            Map.entry("idx_file_meta_expiry", index("file_meta", false, "btree", List.of("expires_at"), List.of("deleted_at is null", "expires_at is not null"))),
            Map.entry("idx_chat_message_study", index("chat_message", false, "btree", List.of("study_id", "id desc"), List.of())),
            Map.entry("idx_notification_recipient", index("notification", false, "btree", List.of("recipient_id", "id desc"), List.of())),
            Map.entry("idx_notification_target", index("notification", false, "btree", List.of("category", "target_screen", "target_id", "target_sub_id"), List.of())),
            Map.entry("idx_field_participant_member", index("field_participant", false, "btree", List.of("member_id", "session_id"), List.of())),
            Map.entry("idx_field_record_author", index("field_record", false, "btree", List.of("author_id", "created_at desc"), List.of("deleted_at is null"))),
            Map.entry("idx_field_record_item", index("field_record", false, "btree", List.of("checklist_item_id", "created_at"), List.of("deleted_at is null"))),
            Map.entry("idx_report_apartment_status", index("report", false, "btree", List.of("apartment_id", "status", "completed_at desc"), List.of())),
            Map.entry("idx_report_favorite_member", index("report_favorite", false, "btree", List.of("member_id", "id desc"), List.of())),
            Map.entry("idx_report_evidence_report", index("report_evidence", false, "btree", List.of("report_id", "display_order", "id"), List.of())),
            Map.entry("idx_post_board_recent", index("post", false, "btree", List.of("board_type", "status", "created_at desc"), List.of("deleted_at is null"))),
            Map.entry("idx_post_hot_candidates", index("post", false, "btree", List.of("board_type", "status", "view_count desc", "created_at desc"), List.of("deleted_at is null"))),
            Map.entry("idx_post_author", index("post", false, "btree", List.of("author_id", "id desc"), List.of())),
            Map.entry("uq_post_auto_report_per_report", index("post", true, "btree", List.of("report_id"), List.of("is_auto_report", "report_id is not null"))),
            Map.entry("idx_post_comment_post", index("post_comment", false, "btree", List.of("post_id", "id"), List.of())),
            Map.entry("idx_post_comment_author", index("post_comment", false, "btree", List.of("author_id", "id desc"), List.of())),
            Map.entry("idx_post_like_post", index("post_like", false, "btree", List.of("post_id", "id"), List.of())),
            Map.entry("idx_post_attachment_post", index("post_attachment", false, "btree", List.of("post_id", "display_order"), List.of())),
            Map.entry("idx_post_search_trgm", index("post", false, "gin", List.of("title", "coalesce", "content", "gin_trgm_ops"), List.of())),
            Map.entry("idx_follow_follower", index("follow", false, "btree", List.of("follower_id", "id desc"), List.of())),
            Map.entry("idx_chatbot_conversation_member", index("chatbot_conversation", false, "btree", List.of("member_id", "last_message_at desc"), List.of())),
            Map.entry("idx_chatbot_message_conv", index("chatbot_message", false, "btree", List.of("conversation_id", "id"), List.of())),
            Map.entry("idx_chatbot_message_processing", index("chatbot_message", false, "btree", List.of("status", "created_at"), List.of("status", "pending", "processing"))),
            Map.entry("uq_chat_message_sender_client_message", index("chat_message", true, "btree", List.of("sender_id", "client_message_id"), List.of("sender_id is not null", "client_message_id is not null"))),
            Map.entry("idx_stt_job_member_status", index("stt_job", false, "btree", List.of("member_id", "status"), List.of())),
            Map.entry("idx_stt_job_study_status", index("stt_job", false, "btree", List.of("study_id", "status"), List.of())),
            Map.entry("idx_stt_job_attempt_job", index("stt_job_attempt", false, "btree", List.of("stt_job_id", "attempt_no"), List.of())),
            Map.entry("uq_post_attachment_file", index("post_attachment", true, "btree", List.of("file_id"), List.of())),
            Map.entry("idx_study_notice_active_study_id_id", index("study_notice", false, "btree", List.of("study_id", "id desc"), List.of("deleted_at is null"))),
            Map.entry("idx_field_visit_start_request_session", index("field_visit_start_request", false, "btree", List.of("session_id"), List.of())),
            Map.entry("idx_post_comment_post_created_active", index("post_comment", false, "btree", List.of("post_id", "created_at", "id"), List.of("deleted_at is null"))),
            Map.entry("idx_stt_dispatch_outbox_ready", index("stt_dispatch_outbox", false, "btree", List.of("status", "next_attempt_at", "id"), List.of())),
            Map.entry("idx_stt_audio_cleanup_ready", index("stt_audio_cleanup_job", false, "btree", List.of("status", "next_attempt_at", "id"), List.of())),
            Map.entry("idx_route_waypoint_item_checklist_item", index("field_visit_route_waypoint_item", false, "btree", List.of("checklist_item_id"), List.of())),
            Map.entry("idx_field_visit_close_vote_session_id", index("field_visit_close_vote", false, "btree", List.of("field_session_id"), List.of())),
            Map.entry("idx_notification_recipient_read_sent", index("notification", false, "btree", List.of("recipient_id", "is_read", "sent_at desc", "id desc"), List.of())),
            Map.entry("idx_member_review_reviewee_created", index("member_review", false, "btree", List.of("reviewee_id", "created_at desc", "id desc"), List.of()))
    );

    private static final List<String> EXPECTED_HOT_VIEW_FRAGMENTS = List.of(
            "from post_like",
            "from post_comment",
            "deleted_at is null",
            "view_count",
            "like_count",
            "comment_count",
            "greatest",
            "168",
            "3600",
            "0.1",
            "status",
            "active",
            "7 days",
            ">=",
            "20",
            "dense_rank",
            "partition by",
            "board_type",
            "order by",
            "hot_score",
            "created_at",
            "post_id",
            "desc"
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES = Be033PostgresContainerFactory.create();

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM member WHERE email LIKE 'be033-constraint-%'");
    }

    @Test
    void 현재_운영_스키마의_FK_UNIQUE_CHECK_정의가_확정_매니페스트와_일치한다() {
        assertThat(loadForeignKeys())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_FOREIGN_KEYS);
        assertThat(loadCascadeForeignKeys())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_CASCADE_FOREIGN_KEYS);
        assertThat(loadUniqueConstraints())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_UNIQUES);
        assertThat(loadCheckConstraints())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_CHECKS);
        assertThat(loadDeferrableConstraintCount()).isZero();

        Map<String, String> checkDefinitions = loadCheckDefinitions();
        assertThat(EXPECTED_CHECK_FRAGMENTS.keySet())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_CHECKS);
        EXPECTED_CHECK_FRAGMENTS.forEach((constraint, fragments) -> {
            String actual = checkDefinitions.get(constraint);
            assertThat(actual)
                    .as("CHECK definition for %s", constraint)
                    .isNotNull();
            assertContainsFragments(actual, fragments, "CHECK " + constraint);
        });
    }

    @Test
    void 현재_운영_스키마의_명시적_인덱스_정의와_HOT_VIEW를_검증한다() {
        Map<String, IndexMetadata> indexes = loadIndexMetadata();
        assertThat(EXPECTED_INDEX_DEFINITIONS.keySet())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_INDEXES);
        EXPECTED_INDEX_DEFINITIONS.forEach((name, expected) -> {
            IndexMetadata actual = indexes.get(name);
            assertThat(actual).as("index metadata for %s", name).isNotNull();
            assertThat(actual.table()).as("index table for %s", name)
                    .isEqualTo(expected.table());
            assertThat(actual.unique()).as("index uniqueness for %s", name)
                    .isEqualTo(expected.unique());
            assertThat(actual.method()).as("index method for %s", name)
                    .isEqualTo(expected.method());
            assertOrderedFragments(
                    actual.keyDefinition(),
                    expected.keyFragments(),
                    "index keys " + name
            );
            if (expected.predicateFragments().isEmpty()) {
                assertThat(actual.predicate())
                        .as("index predicate for %s", name)
                        .isBlank();
            } else {
                assertContainsFragments(
                        actual.predicate(),
                        expected.predicateFragments(),
                        "index predicate " + name
                );
            }
        });

        List<String> viewColumns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'post_hot_metric'
                ORDER BY ordinal_position
                """, String.class);
        assertThat(viewColumns).containsExactly(
                "post_id", "board_type", "view_count", "like_count",
                "comment_count", "hot_score", "is_hot", "hot_rank", "created_at"
        );
        String viewDefinition = jdbcTemplate.queryForObject("""
                SELECT definition FROM pg_views
                WHERE schemaname = 'public' AND viewname = 'post_hot_metric'
                """, String.class);
        assertContainsFragments(
                viewDefinition,
                EXPECTED_HOT_VIEW_FRAGMENTS,
                "post_hot_metric view"
        );
    }

    @Test
    void 대표_FK_UNIQUE_CHECK_위반_DML은_PostgreSQL이_거부한다() {
        assertConstraintViolation(() -> jdbcTemplate.update("""
                INSERT INTO member_preference (member_id) VALUES (9223372036854770000)
                """), "23503", "member_preference_member_id_fkey");

        jdbcTemplate.update("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES ('be033-constraint-unique@test.local',
                          'be033-constraint-a', false, true, false)
                """);
        assertConstraintViolation(() -> jdbcTemplate.update("""
                INSERT INTO member (
                    email, nickname, age_group_public_agreed,
                    service_notification_agreed, ad_notification_agreed
                ) VALUES ('be033-constraint-unique@test.local',
                          'be033-constraint-b', false, true, false)
                """), "23505", "member_email_key");

        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM member WHERE email = 'be033-constraint-unique@test.local'",
                Long.class
        );
        assertConstraintViolation(() -> jdbcTemplate.update("""
                INSERT INTO follow (follower_id, following_id) VALUES (?, ?)
                """, memberId, memberId), "23514", "follow_check");
    }

    private Set<String> loadForeignKeys() {
        return loadForeignKeys(false);
    }

    private Set<String> loadCascadeForeignKeys() {
        return loadForeignKeys(true);
    }

    private Set<String> loadForeignKeys(boolean cascadeOnly) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT format(
                    '%s(%s)->%s(%s)',
                    source.relname,
                    string_agg(source_column.attname, ',' ORDER BY source_key.ordinality),
                    target.relname,
                    string_agg(target_column.attname, ',' ORDER BY source_key.ordinality)
                )
                FROM pg_constraint constraint_def
                JOIN pg_class source ON source.oid = constraint_def.conrelid
                JOIN pg_namespace namespace ON namespace.oid = source.relnamespace
                JOIN pg_class target ON target.oid = constraint_def.confrelid
                CROSS JOIN LATERAL unnest(constraint_def.conkey)
                    WITH ORDINALITY AS source_key(attnum, ordinality)
                JOIN LATERAL unnest(constraint_def.confkey)
                    WITH ORDINALITY AS target_key(attnum, ordinality)
                    ON target_key.ordinality = source_key.ordinality
                JOIN pg_attribute source_column
                    ON source_column.attrelid = source.oid
                    AND source_column.attnum = source_key.attnum
                JOIN pg_attribute target_column
                    ON target_column.attrelid = target.oid
                    AND target_column.attnum = target_key.attnum
                WHERE namespace.nspname = 'public'
                  AND constraint_def.contype = 'f'
                  AND (? = FALSE OR constraint_def.confdeltype = 'c')
                GROUP BY constraint_def.oid, source.relname, target.relname
                """, String.class, cascadeOnly));
    }

    private Set<String> loadUniqueConstraints() {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT format(
                    '%s(%s)',
                    relation.relname,
                    string_agg(column_def.attname, ',' ORDER BY key_def.ordinality)
                )
                FROM pg_constraint constraint_def
                JOIN pg_class relation ON relation.oid = constraint_def.conrelid
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                CROSS JOIN LATERAL unnest(constraint_def.conkey)
                    WITH ORDINALITY AS key_def(attnum, ordinality)
                JOIN pg_attribute column_def
                    ON column_def.attrelid = relation.oid
                    AND column_def.attnum = key_def.attnum
                WHERE namespace.nspname = 'public' AND constraint_def.contype = 'u'
                GROUP BY constraint_def.oid, relation.relname
                """, String.class));
    }

    private Set<String> loadCheckConstraints() {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT relation.relname || '.' || constraint_def.conname
                FROM pg_constraint constraint_def
                JOIN pg_class relation ON relation.oid = constraint_def.conrelid
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname <> 'spatial_ref_sys'
                  AND constraint_def.contype = 'c'
                """, String.class));
    }

    private int loadDeferrableConstraintCount() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint constraint_def
                JOIN pg_class relation ON relation.oid = constraint_def.conrelid
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND constraint_def.contype IN ('f', 'u')
                  AND constraint_def.condeferrable
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private Map<String, String> loadCheckDefinitions() {
        Map<String, String> definitions = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT relation.relname || '.' || constraint_def.conname AS constraint_name,
                       pg_get_constraintdef(constraint_def.oid, TRUE) AS definition
                FROM pg_constraint constraint_def
                JOIN pg_class relation ON relation.oid = constraint_def.conrelid
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname <> 'spatial_ref_sys'
                  AND constraint_def.contype = 'c'
                """, (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> definitions.put(
                resultSet.getString("constraint_name"),
                resultSet.getString("definition")
        ));
        return definitions;
    }

    private Map<String, IndexMetadata> loadIndexMetadata() {
        Map<String, IndexMetadata> indexes = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT index_relation.relname AS index_name,
                       table_relation.relname AS table_name,
                       index_def.indisunique AS is_unique,
                       access_method.amname AS access_method,
                       pg_get_indexdef(index_def.indexrelid) AS key_definition,
                       COALESCE(
                           pg_get_expr(index_def.indpred, index_def.indrelid),
                           ''
                       ) AS predicate
                FROM pg_index index_def
                JOIN pg_class index_relation
                  ON index_relation.oid = index_def.indexrelid
                JOIN pg_class table_relation
                  ON table_relation.oid = index_def.indrelid
                JOIN pg_namespace namespace
                  ON namespace.oid = table_relation.relnamespace
                JOIN pg_am access_method
                  ON access_method.oid = index_relation.relam
                WHERE namespace.nspname = 'public'
                """, (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> indexes.put(
                resultSet.getString("index_name"),
                new IndexMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getBoolean("is_unique"),
                        resultSet.getString("access_method"),
                        resultSet.getString("key_definition"),
                        resultSet.getString("predicate")
                )
        ));
        return indexes;
    }

    private void assertConstraintViolation(
            ThrowingCallable action,
            String expectedSqlState,
            String expectedConstraint
    ) {
        Throwable thrown = catchThrowable(action);
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class);
        SQLException sqlException = findSqlException(thrown);
        assertThat(sqlException == null)
                .as("PostgreSQL exception for %s", expectedConstraint)
                .isFalse();
        assertThat(sqlException.getSQLState()).isEqualTo(expectedSqlState);
        assertThat(sqlException.getMessage())
                .containsIgnoringCase(expectedConstraint);
    }

    private SQLException findSqlException(Throwable thrown) {
        Throwable current = thrown;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static void assertContainsFragments(
            String actual,
            List<String> fragments,
            String description
    ) {
        String normalized = normalize(actual);
        fragments.forEach(fragment -> assertThat(normalized)
                .as("%s contains %s", description, fragment)
                .contains(normalize(fragment)));
    }

    private static void assertOrderedFragments(
            String actual,
            List<String> fragments,
            String description
    ) {
        String normalized = normalize(actual);
        int cursor = -1;
        for (String fragment : fragments) {
            int index = normalized.indexOf(normalize(fragment), cursor + 1);
            assertThat(index)
                    .as("%s contains %s after position %s", description, fragment, cursor)
                    .isGreaterThan(cursor);
            cursor = index;
        }
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private static IndexExpectation index(
            String table,
            boolean unique,
            String method,
            List<String> keyFragments,
            List<String> predicateFragments
    ) {
        return new IndexExpectation(
                table,
                unique,
                method,
                keyFragments,
                predicateFragments
        );
    }

    private record IndexExpectation(
            String table,
            boolean unique,
            String method,
            List<String> keyFragments,
            List<String> predicateFragments
    ) {
    }

    private record IndexMetadata(
            String table,
            boolean unique,
            String method,
            String keyDefinition,
            String predicate
    ) {
    }
}
