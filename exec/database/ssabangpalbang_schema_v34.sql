--
-- PostgreSQL database dump
--

\restrict 1Yyc9MjemAIlZBCx7rV8Ko21RgDhnu25ZT0URn9UtUt7vq1N1IBtY8fJPwiwSdJ

-- Dumped from database version 17.10 (Debian 17.10-1.pgdg13+1)
-- Dumped by pg_dump version 17.10 (Debian 17.10-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: postgis; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;


--
-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION postgis IS 'PostGIS geometry and geography spatial types and functions';


--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: apartment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apartment (
    id bigint NOT NULL,
    complex_code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    address character varying(300),
    district_code character varying(10),
    district_name character varying(50),
    dong_name character varying(50),
    legal_dong_code character varying(20),
    longitude double precision NOT NULL,
    latitude double precision NOT NULL,
    household_count integer,
    completion_year_month character varying(7),
    parking_space_count integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT apartment_completion_year_month_check CHECK (((completion_year_month IS NULL) OR ((completion_year_month)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text))),
    CONSTRAINT apartment_household_count_check CHECK (((household_count IS NULL) OR (household_count >= 0))),
    CONSTRAINT apartment_parking_space_count_check CHECK (((parking_space_count IS NULL) OR (parking_space_count >= 0)))
);


--
-- Name: apartment_favorite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apartment_favorite (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    apartment_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: apartment_favorite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.apartment_favorite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: apartment_favorite_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.apartment_favorite_id_seq OWNED BY public.apartment_favorite.id;


--
-- Name: apartment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.apartment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: apartment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.apartment_id_seq OWNED BY public.apartment.id;


--
-- Name: apartment_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apartment_image (
    apartment_id bigint NOT NULL,
    object_key character varying(255) NOT NULL,
    sha256 character(64) NOT NULL,
    source_slug character varying(100) NOT NULL,
    source_csv character varying(500) NOT NULL,
    match_method character varying(64) NOT NULL,
    match_confidence character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_apartment_image_match_confidence CHECK (((match_confidence)::text = ANY ((ARRAY['HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying])::text[]))),
    CONSTRAINT chk_apartment_image_sha256 CHECK ((sha256 ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: TABLE apartment_image; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.apartment_image IS '아파트 대표 일러스트의 공개 object key와 검증 가능한 매칭 메타데이터';


--
-- Name: COLUMN apartment_image.object_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.apartment_image.object_key IS '정적 이미지 호스트의 base URL 뒤에 결합할 상대 object key';


--
-- Name: apartment_rag_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apartment_rag_document (
    id bigint NOT NULL,
    apartment_id bigint NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint,
    content text NOT NULL,
    embedding public.vector(768),
    source_at timestamp with time zone,
    reindex_key character varying(200) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: apartment_rag_document_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.apartment_rag_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: apartment_rag_document_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.apartment_rag_document_id_seq OWNED BY public.apartment_rag_document.id;


--
-- Name: apartment_transaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apartment_transaction (
    id bigint NOT NULL,
    apartment_id bigint NOT NULL,
    deal_date date NOT NULL,
    exclusive_area numeric(8,2),
    price bigint,
    floor integer,
    is_canceled boolean DEFAULT false NOT NULL,
    dedup_key character varying(200) NOT NULL,
    collected_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: apartment_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.apartment_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: apartment_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.apartment_transaction_id_seq OWNED BY public.apartment_transaction.id;


--
-- Name: chat_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_message (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    sender_id bigint,
    message_type character varying(20) NOT NULL,
    content text,
    image_file_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    client_message_id character varying(100)
);


--
-- Name: chat_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_message_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_message_id_seq OWNED BY public.chat_message.id;


--
-- Name: chat_read_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_read_status (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    member_id bigint NOT NULL,
    last_read_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chat_read_status_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_read_status_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_read_status_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_read_status_id_seq OWNED BY public.chat_read_status.id;


--
-- Name: chatbot_conversation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chatbot_conversation (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    apartment_id bigint NOT NULL,
    last_message_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: chatbot_conversation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chatbot_conversation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chatbot_conversation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chatbot_conversation_id_seq OWNED BY public.chatbot_conversation.id;


--
-- Name: chatbot_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chatbot_message (
    id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    role character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'COMPLETED'::character varying NOT NULL,
    content text,
    basis_type character varying(20),
    basis_label character varying(100),
    sources_json jsonb,
    fail_reason character varying(500),
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chatbot_message_check CHECK (((((role)::text = 'USER'::text) AND ((status)::text = 'COMPLETED'::text) AND (content IS NOT NULL)) OR (((role)::text = 'ASSISTANT'::text) AND ((((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::text[])) AND (content IS NULL)) OR (((status)::text = 'COMPLETED'::text) AND (content IS NOT NULL)) OR ((status)::text = 'FAILED'::text))))),
    CONSTRAINT chatbot_message_check1 CHECK ((((status)::text <> 'FAILED'::text) OR (fail_reason IS NOT NULL))),
    CONSTRAINT chatbot_message_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ASSISTANT'::character varying])::text[]))),
    CONSTRAINT chatbot_message_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: chatbot_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chatbot_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chatbot_message_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chatbot_message_id_seq OWNED BY public.chatbot_message.id;


--
-- Name: checklist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.checklist (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    member_id bigint NOT NULL,
    is_fallback boolean DEFAULT false NOT NULL,
    generated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: checklist_answer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.checklist_answer (
    id bigint NOT NULL,
    checklist_item_id bigint NOT NULL,
    is_completed boolean DEFAULT false NOT NULL,
    completed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: checklist_answer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.checklist_answer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: checklist_answer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.checklist_answer_id_seq OWNED BY public.checklist_answer.id;


--
-- Name: checklist_generation_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.checklist_generation_progress (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    member_id bigint NOT NULL,
    attempt_id character varying(64) NOT NULL,
    status character varying(20) NOT NULL,
    stage character varying(30) NOT NULL,
    progress_rate integer NOT NULL,
    message character varying(255) NOT NULL,
    failure_message character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT checklist_generation_progress_progress_rate_check CHECK (((progress_rate >= 0) AND (progress_rate <= 100))),
    CONSTRAINT checklist_generation_progress_status_check CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: checklist_generation_progress_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.checklist_generation_progress_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: checklist_generation_progress_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.checklist_generation_progress_id_seq OWNED BY public.checklist_generation_progress.id;


--
-- Name: checklist_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.checklist_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: checklist_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.checklist_id_seq OWNED BY public.checklist.id;


--
-- Name: checklist_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.checklist_item (
    id bigint NOT NULL,
    checklist_id bigint NOT NULL,
    category character varying(30) NOT NULL,
    title text NOT NULL,
    subtitle text,
    display_order integer NOT NULL,
    example text,
    CONSTRAINT checklist_item_title_check CHECK ((btrim(title) <> ''::text))
);


--
-- Name: COLUMN checklist_item.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.checklist_item.title IS '체크리스트 항목의 주 제목. API 필드명 title.';


--
-- Name: COLUMN checklist_item.subtitle; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.checklist_item.subtitle IS '체크리스트 항목의 보조 설명. API 필드명 subtitle.';


--
-- Name: COLUMN checklist_item.example; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.checklist_item.example IS '현장에서 이 항목을 확인한 뒤 남길 법한 한 줄 예시 메모. 프론트 메모 입력 placeholder에 사용.';


--
-- Name: checklist_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.checklist_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: checklist_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.checklist_item_id_seq OWNED BY public.checklist_item.id;


--
-- Name: fcm_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fcm_token (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    token character varying(255) NOT NULL,
    device_id character varying(255) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: fcm_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fcm_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fcm_token_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.fcm_token_id_seq OWNED BY public.fcm_token.id;


--
-- Name: field_participant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_participant (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    member_id bigint NOT NULL,
    status character varying(20) DEFAULT 'IN_PROGRESS'::character varying NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    ended_at timestamp with time zone,
    end_reason character varying(30),
    stay_duration_sec integer,
    CONSTRAINT field_participant_stay_duration_sec_check CHECK (((stay_duration_sec IS NULL) OR (stay_duration_sec >= 0)))
);


--
-- Name: field_participant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_participant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_participant_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_participant_id_seq OWNED BY public.field_participant.id;


--
-- Name: field_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_record (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    checklist_item_id bigint NOT NULL,
    author_id bigint NOT NULL,
    source_type character varying(20) NOT NULL,
    text_content text,
    photo_file_id bigint,
    stt_status character varying(20),
    client_request_id character varying(100) NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    request_fingerprint character varying(128)
);


--
-- Name: COLUMN field_record.request_fingerprint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.field_record.request_fingerprint IS 'BE-015 TEXT/PHOTO 생성 시 client_request_id 멱등 비교용. STT는 NULL.';


--
-- Name: field_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_record_id_seq OWNED BY public.field_record.id;


--
-- Name: field_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_session (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    status character varying(20) DEFAULT 'IN_PROGRESS'::character varying NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    ended_at timestamp with time zone,
    ended_by_id bigint,
    end_reason character varying(30)
);


--
-- Name: field_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_session_id_seq OWNED BY public.field_session.id;


--
-- Name: field_visit_candidate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_visit_candidate (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    member_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE field_visit_candidate; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.field_visit_candidate IS 'BE-014 최초 임장 세션 시작 시점의 ACTIVE 스터디 멤버 고정 명단.';


--
-- Name: field_visit_candidate_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_visit_candidate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_visit_candidate_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_visit_candidate_id_seq OWNED BY public.field_visit_candidate.id;


--
-- Name: field_visit_close_vote; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_visit_close_vote (
    id bigint NOT NULL,
    field_session_id bigint NOT NULL,
    field_participant_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: field_visit_close_vote_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_visit_close_vote_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_visit_close_vote_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_visit_close_vote_id_seq OWNED BY public.field_visit_close_vote.id;


--
-- Name: field_visit_route; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_visit_route (
    id bigint NOT NULL,
    session_id bigint NOT NULL,
    generated_by_id bigint NOT NULL,
    total_distance_meters integer DEFAULT 0 NOT NULL,
    estimated_duration_minutes integer DEFAULT 0 NOT NULL,
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    geometry jsonb,
    CONSTRAINT ck_route_distance CHECK ((total_distance_meters >= 0)),
    CONSTRAINT ck_route_duration CHECK ((estimated_duration_minutes >= 0))
);


--
-- Name: field_visit_route_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_visit_route_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_visit_route_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_visit_route_id_seq OWNED BY public.field_visit_route.id;


--
-- Name: field_visit_route_waypoint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_visit_route_waypoint (
    id bigint NOT NULL,
    route_id bigint NOT NULL,
    sequence integer NOT NULL,
    facility_type character varying(30) NOT NULL,
    name character varying(100) NOT NULL,
    address character varying(300),
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    kakao_place_id character varying(50),
    distance_from_origin_m integer DEFAULT 0 NOT NULL,
    distance_from_prev_m integer DEFAULT 0 NOT NULL,
    walk_minutes_from_prev integer DEFAULT 0 NOT NULL,
    stay_minutes integer DEFAULT 5 NOT NULL,
    guide text,
    CONSTRAINT ck_route_waypoint_lat CHECK (((latitude >= ('-90'::integer)::double precision) AND (latitude <= (90)::double precision))),
    CONSTRAINT ck_route_waypoint_lng CHECK (((longitude >= ('-180'::integer)::double precision) AND (longitude <= (180)::double precision))),
    CONSTRAINT ck_route_waypoint_name CHECK ((btrim((name)::text) <> ''::text)),
    CONSTRAINT ck_route_waypoint_sequence CHECK ((sequence >= 1))
);


--
-- Name: field_visit_route_waypoint_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_visit_route_waypoint_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_visit_route_waypoint_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_visit_route_waypoint_id_seq OWNED BY public.field_visit_route_waypoint.id;


--
-- Name: field_visit_route_waypoint_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_visit_route_waypoint_item (
    id bigint NOT NULL,
    waypoint_id bigint NOT NULL,
    checklist_item_id bigint NOT NULL
);


--
-- Name: field_visit_route_waypoint_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_visit_route_waypoint_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_visit_route_waypoint_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_visit_route_waypoint_item_id_seq OWNED BY public.field_visit_route_waypoint_item.id;


--
-- Name: field_visit_start_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.field_visit_start_request (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    study_id bigint NOT NULL,
    session_id bigint NOT NULL,
    participant_id bigint NOT NULL,
    client_request_id character varying(100) NOT NULL,
    request_fingerprint character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE field_visit_start_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.field_visit_start_request IS 'BE-014 임장 시작 client_request_id 멱등 이력. member_id+client_request_id UNIQUE.';


--
-- Name: COLUMN field_visit_start_request.request_fingerprint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.field_visit_start_request.request_fingerprint IS 'studyId|정규화 latitude|정규화 longitude SHA-256 hex.';


--
-- Name: field_visit_start_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.field_visit_start_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: field_visit_start_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.field_visit_start_request_id_seq OWNED BY public.field_visit_start_request.id;


--
-- Name: file_meta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.file_meta (
    id bigint NOT NULL,
    owner_id bigint NOT NULL,
    study_id bigint,
    file_usage character varying(30) NOT NULL,
    original_name character varying(255),
    s3_key character varying(500) NOT NULL,
    content_type character varying(100),
    size_bytes bigint,
    upload_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    expires_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT file_meta_size_bytes_check CHECK (((size_bytes IS NULL) OR (size_bytes >= 0)))
);


--
-- Name: file_meta_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.file_meta_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: file_meta_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.file_meta_id_seq OWNED BY public.file_meta.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: follow; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.follow (
    id bigint NOT NULL,
    follower_id bigint NOT NULL,
    following_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT follow_check CHECK ((follower_id <> following_id))
);


--
-- Name: follow_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.follow_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: follow_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.follow_id_seq OWNED BY public.follow.id;


--
-- Name: member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255),
    nickname character varying(50) NOT NULL,
    profile_image_url character varying(500),
    selected_character_id character varying(20) DEFAULT 'PALBANG'::character varying NOT NULL,
    age_group character varying(20),
    age_group_public_agreed boolean DEFAULT false NOT NULL,
    service_notification_agreed boolean DEFAULT true NOT NULL,
    ad_notification_agreed boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: member_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: member_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.member_id_seq OWNED BY public.member.id;


--
-- Name: member_preference; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member_preference (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    purpose character varying(50),
    household_type character varying(50),
    budget character varying(50),
    interest_region character varying(100),
    interest_region_public_agreed boolean DEFAULT false NOT NULL,
    priorities jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    marital_status character varying(20),
    has_vehicle boolean,
    has_children boolean
);


--
-- Name: COLUMN member_preference.interest_region_public_agreed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.member_preference.interest_region_public_agreed IS 'TRUE인 경우에만 공개 프로필/팔로잉 목록에 interest_region을 노출한다.';


--
-- Name: member_preference_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.member_preference_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: member_preference_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.member_preference_id_seq OWNED BY public.member_preference.id;


--
-- Name: member_review; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member_review (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    reviewer_id bigint NOT NULL,
    reviewee_id bigint NOT NULL,
    rating integer,
    content character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    liked boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_member_review_not_self CHECK ((reviewer_id <> reviewee_id)),
    CONSTRAINT ck_member_review_rating CHECK (((rating >= 1) AND (rating <= 5)))
);


--
-- Name: member_review_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.member_review_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: member_review_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.member_review_id_seq OWNED BY public.member_review.id;


--
-- Name: member_review_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.member_review_tag (
    id bigint NOT NULL,
    review_id bigint NOT NULL,
    tag_code character varying(40) NOT NULL
);


--
-- Name: member_review_tag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.member_review_tag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: member_review_tag_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.member_review_tag_id_seq OWNED BY public.member_review_tag.id;


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    recipient_id bigint NOT NULL,
    actor_id bigint,
    category character varying(30) DEFAULT 'SYSTEM'::character varying NOT NULL,
    type character varying(30) NOT NULL,
    target_screen character varying(50),
    target_id bigint,
    target_sub_id bigint,
    title character varying(200),
    body character varying(500),
    send_status character varying(20),
    fail_reason character varying(255),
    is_read boolean DEFAULT false NOT NULL,
    read_at timestamp with time zone,
    idempotency_key character varying(200) NOT NULL,
    sent_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: COLUMN notification.category; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.category IS '알림 상위 분류. type보다 넓은 화면/API 분류 단위.';


--
-- Name: COLUMN notification.target_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.target_id IS '딥링크의 주 대상 ID. 예: studyId, reportId, postId.';


--
-- Name: COLUMN notification.target_sub_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.target_sub_id IS '딥링크의 보조 대상 ID. 예: commentId, scheduleId. 없으면 NULL.';


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_id_seq OWNED BY public.notification.id;


--
-- Name: post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post (
    id bigint NOT NULL,
    board_type character varying(20) NOT NULL,
    author_id bigint,
    title character varying(200) NOT NULL,
    content text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    is_auto_report boolean DEFAULT false NOT NULL,
    report_id bigint,
    apartment_id bigint,
    view_count bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_post_board_type CHECK (((board_type)::text = ANY ((ARRAY['INFORMATION'::character varying, 'FREE'::character varying])::text[]))),
    CONSTRAINT ck_post_content_length CHECK (((content IS NOT NULL) AND ((char_length(btrim(content)) >= 1) AND (char_length(btrim(content)) <= 5000)))),
    CONSTRAINT ck_post_origin CHECK ((((NOT is_auto_report) AND (author_id IS NOT NULL) AND (report_id IS NULL)) OR (is_auto_report AND ((board_type)::text = 'INFORMATION'::text) AND (report_id IS NOT NULL)))),
    CONSTRAINT ck_post_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'HIDDEN'::character varying])::text[]))),
    CONSTRAINT ck_post_title_not_blank CHECK (((char_length(btrim((title)::text)) >= 1) AND (char_length(btrim((title)::text)) <= 200))),
    CONSTRAINT post_check CHECK (((NOT is_auto_report) OR ((report_id IS NOT NULL) AND ((board_type)::text = 'INFORMATION'::text)))),
    CONSTRAINT post_view_count_check CHECK ((view_count >= 0))
);


--
-- Name: post_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_attachment (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    file_id bigint NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: post_attachment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.post_attachment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: post_attachment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.post_attachment_id_seq OWNED BY public.post_attachment.id;


--
-- Name: post_comment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_comment (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    author_id bigint NOT NULL,
    content text NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: post_comment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.post_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: post_comment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.post_comment_id_seq OWNED BY public.post_comment.id;


--
-- Name: post_like; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_like (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    member_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: post_hot_metric; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.post_hot_metric AS
 WITH like_count AS (
         SELECT post_like.post_id,
            count(*) AS like_count
           FROM public.post_like
          GROUP BY post_like.post_id
        ), comment_count AS (
         SELECT post_comment.post_id,
            count(*) AS comment_count
           FROM public.post_comment
          WHERE (post_comment.deleted_at IS NULL)
          GROUP BY post_comment.post_id
        ), metrics AS (
         SELECT p.id AS post_id,
            p.board_type,
            p.created_at,
            p.view_count,
            COALESCE(l.like_count, (0)::bigint) AS like_count,
            COALESCE(c.comment_count, (0)::bigint) AS comment_count,
            round(((((p.view_count)::numeric + ((COALESCE(l.like_count, (0)::bigint))::numeric * (5)::numeric)) + ((COALESCE(c.comment_count, (0)::bigint))::numeric * (3)::numeric)) + (GREATEST((0)::numeric, ((168)::numeric - (EXTRACT(epoch FROM (now() - p.created_at)) / (3600)::numeric))) * 0.1)), 2) AS hot_score
           FROM ((public.post p
             LEFT JOIN like_count l ON ((l.post_id = p.id)))
             LEFT JOIN comment_count c ON ((c.post_id = p.id)))
          WHERE (((p.status)::text = 'ACTIVE'::text) AND (p.deleted_at IS NULL) AND (p.created_at >= (now() - '7 days'::interval)))
        )
 SELECT post_id,
    board_type,
    view_count,
    like_count,
    comment_count,
    hot_score,
    (hot_score >= (20)::numeric) AS is_hot,
    dense_rank() OVER (PARTITION BY board_type ORDER BY hot_score DESC, created_at DESC, post_id DESC) AS hot_rank,
    created_at
   FROM metrics;


--
-- Name: VIEW post_hot_metric; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON VIEW public.post_hot_metric IS '7일 이내 ACTIVE 게시글에 조회×1, 좋아요×5, 댓글×3, 최근성 보너스를 적용하고 20점 이상을 HOT으로 판정한다.';


--
-- Name: post_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.post_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: post_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.post_id_seq OWNED BY public.post.id;


--
-- Name: post_like_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.post_like_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: post_like_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.post_like_id_seq OWNED BY public.post_like.id;


--
-- Name: report; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    apartment_id bigint NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    progress_stage character varying(20),
    result_json jsonb,
    fail_reason character varying(255),
    is_retryable boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    field_session_id bigint NOT NULL,
    processing_token_hash character varying(64),
    processing_attempt integer DEFAULT 0 NOT NULL,
    processing_lease_expires_at timestamp with time zone,
    complete_payload_hash character varying(64),
    fail_code character varying(100),
    failed_at timestamp with time zone,
    fail_payload_hash character varying(64),
    retry_requested_at timestamp with time zone,
    CONSTRAINT ck_report_complete_payload_hash CHECK (((complete_payload_hash IS NULL) OR ((complete_payload_hash)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_report_fail_code CHECK (((fail_code IS NULL) OR ((fail_code)::text ~ '^[A-Z][A-Z0-9_]{0,99}$'::text))),
    CONSTRAINT ck_report_fail_payload_hash CHECK (((fail_payload_hash IS NULL) OR ((fail_payload_hash)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT ck_report_processing_attempt CHECK ((processing_attempt >= 0)),
    CONSTRAINT ck_report_processing_token_hash CHECK (((processing_token_hash IS NULL) OR ((processing_token_hash)::text ~ '^[0-9a-f]{64}$'::text)))
);


--
-- Name: COLUMN report.field_session_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.field_session_id IS 'BE-019 리포트 생성의 권위 있는 종료 임장 세션 ID.';


--
-- Name: COLUMN report.processing_token_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.processing_token_hash IS 'Worker 처리권 원문의 SHA-256 해시. 원문 Token은 저장하지 않는다.';


--
-- Name: COLUMN report.processing_attempt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.processing_attempt IS '처리권 신규 발급 또는 Lease 만료 재선점 횟수.';


--
-- Name: COLUMN report.processing_lease_expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.processing_lease_expires_at IS '현재 Worker 처리권의 Backend 관리 만료 시각.';


--
-- Name: COLUMN report.complete_payload_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.complete_payload_hash IS 'AI 결과·근거 payload의 정규화 SHA-256 해시. Token·attempt와 함께 멱등 판정.';


--
-- Name: COLUMN report.fail_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.fail_code IS 'AI Report Worker의 allowlist 실패 코드.';


--
-- Name: COLUMN report.failed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.failed_at IS '현재 처리 시도의 실패가 원자적으로 확정된 시각.';


--
-- Name: COLUMN report.fail_payload_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.fail_payload_hash IS '실패 단계·코드·안전 문구·retryable의 정규화 SHA-256 해시.';


--
-- Name: COLUMN report.retry_requested_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.report.retry_requested_at IS '현재 리포트 재생성 주기의 최초 수락 시각. 중복 요청 응답에 재사용한다.';


--
-- Name: report_evidence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report_evidence (
    id bigint NOT NULL,
    report_id bigint NOT NULL,
    field_record_id bigint NOT NULL,
    claim_key character varying(100),
    display_order integer
);


--
-- Name: report_evidence_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.report_evidence_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_evidence_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.report_evidence_id_seq OWNED BY public.report_evidence.id;


--
-- Name: report_favorite; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report_favorite (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    report_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: report_favorite_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.report_favorite_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_favorite_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.report_favorite_id_seq OWNED BY public.report_favorite.id;


--
-- Name: report_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: report_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.report_id_seq OWNED BY public.report.id;


--
-- Name: schedule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schedule (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    start_at timestamp with time zone NOT NULL,
    end_at timestamp with time zone,
    meeting_place character varying(200),
    status character varying(20) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT schedule_check CHECK (((end_at IS NULL) OR (end_at >= start_at)))
);


--
-- Name: schedule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.schedule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: schedule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.schedule_id_seq OWNED BY public.schedule.id;


--
-- Name: social_account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.social_account (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    provider character varying(20) NOT NULL,
    social_user_id character varying(255) NOT NULL,
    email character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: social_account_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.social_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: social_account_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.social_account_id_seq OWNED BY public.social_account.id;


--
-- Name: stt_audio_cleanup_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stt_audio_cleanup_job (
    id bigint NOT NULL,
    audio_file_id bigint NOT NULL,
    object_key character varying(500) NOT NULL,
    status character varying(20) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    last_error character varying(500),
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_stt_audio_cleanup_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_stt_audio_cleanup_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: stt_audio_cleanup_job_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stt_audio_cleanup_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stt_audio_cleanup_job_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stt_audio_cleanup_job_id_seq OWNED BY public.stt_audio_cleanup_job.id;


--
-- Name: stt_dispatch_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stt_dispatch_outbox (
    id bigint NOT NULL,
    stt_job_id bigint NOT NULL,
    stt_id character varying(50) NOT NULL,
    attempt_no integer NOT NULL,
    audio_file_id bigint NOT NULL,
    object_key character varying(500) NOT NULL,
    content_type character varying(100) NOT NULL,
    language character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    last_error character varying(500),
    published_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_stt_dispatch_outbox_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_stt_dispatch_outbox_attempt_no CHECK ((attempt_no >= 1)),
    CONSTRAINT ck_stt_dispatch_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: stt_dispatch_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stt_dispatch_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stt_dispatch_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stt_dispatch_outbox_id_seq OWNED BY public.stt_dispatch_outbox.id;


--
-- Name: stt_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stt_job (
    id bigint NOT NULL,
    stt_id character varying(50) NOT NULL,
    member_id bigint NOT NULL,
    study_id bigint NOT NULL,
    session_id bigint NOT NULL,
    audio_file_id bigint NOT NULL,
    checklist_item_id bigint NOT NULL,
    field_record_id bigint,
    initial_client_request_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    fail_code character varying(100),
    fail_reason character varying(500),
    retryable boolean DEFAULT false NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    last_dispatched_at timestamp with time zone,
    dispatch_count integer DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_stt_job_dispatch_count CHECK ((dispatch_count >= 0)),
    CONSTRAINT ck_stt_job_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT ck_stt_job_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: stt_job_attempt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stt_job_attempt (
    id bigint NOT NULL,
    stt_job_id bigint NOT NULL,
    member_id bigint NOT NULL,
    client_request_id uuid NOT NULL,
    attempt_no integer NOT NULL,
    request_type character varying(10) NOT NULL,
    status character varying(20) NOT NULL,
    fail_code character varying(100),
    fail_reason character varying(500),
    requested_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    CONSTRAINT ck_stt_job_attempt_no CHECK ((attempt_no >= 1)),
    CONSTRAINT ck_stt_job_attempt_request_type CHECK (((request_type)::text = ANY ((ARRAY['INITIAL'::character varying, 'RETRY'::character varying])::text[]))),
    CONSTRAINT ck_stt_job_attempt_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: stt_job_attempt_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stt_job_attempt_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stt_job_attempt_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stt_job_attempt_id_seq OWNED BY public.stt_job_attempt.id;


--
-- Name: stt_job_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stt_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stt_job_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stt_job_id_seq OWNED BY public.stt_job.id;


--
-- Name: study; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study (
    id bigint NOT NULL,
    apartment_id bigint NOT NULL,
    leader_id bigint NOT NULL,
    title character varying(200),
    intro text,
    goal text NOT NULL,
    capacity integer NOT NULL,
    purpose character varying(50),
    status character varying(20) DEFAULT 'RECRUITING'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    recruitment_closed_at timestamp with time zone,
    canceled_at timestamp with time zone,
    CONSTRAINT study_capacity_check CHECK ((capacity > 0)),
    CONSTRAINT study_goal_check CHECK ((btrim(goal) <> ''::text))
);


--
-- Name: COLUMN study.goal; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.study.goal IS '스터디 생성 및 모집/내 스터디 상세 화면에 표시하는 스터디 목표.';


--
-- Name: study_application; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_application (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    applicant_id bigint NOT NULL,
    intro character varying(200),
    purpose character varying(20),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    decided_at timestamp with time zone
);


--
-- Name: study_application_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.study_application_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: study_application_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.study_application_id_seq OWNED BY public.study_application.id;


--
-- Name: study_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.study_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: study_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.study_id_seq OWNED BY public.study.id;


--
-- Name: study_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_member (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    member_id bigint NOT NULL,
    role character varying(20) DEFAULT 'MEMBER'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    joined_at timestamp with time zone DEFAULT now() NOT NULL,
    left_at timestamp with time zone,
    chat_push_enabled boolean DEFAULT true NOT NULL
);


--
-- Name: study_member_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.study_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: study_member_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.study_member_id_seq OWNED BY public.study_member.id;


--
-- Name: study_notice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.study_notice (
    id bigint NOT NULL,
    study_id bigint NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: study_notice_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.study_notice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: study_notice_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.study_notice_id_seq OWNED BY public.study_notice.id;


--
-- Name: apartment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment ALTER COLUMN id SET DEFAULT nextval('public.apartment_id_seq'::regclass);


--
-- Name: apartment_favorite id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_favorite ALTER COLUMN id SET DEFAULT nextval('public.apartment_favorite_id_seq'::regclass);


--
-- Name: apartment_rag_document id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_rag_document ALTER COLUMN id SET DEFAULT nextval('public.apartment_rag_document_id_seq'::regclass);


--
-- Name: apartment_transaction id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_transaction ALTER COLUMN id SET DEFAULT nextval('public.apartment_transaction_id_seq'::regclass);


--
-- Name: chat_message id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message ALTER COLUMN id SET DEFAULT nextval('public.chat_message_id_seq'::regclass);


--
-- Name: chat_read_status id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_read_status ALTER COLUMN id SET DEFAULT nextval('public.chat_read_status_id_seq'::regclass);


--
-- Name: chatbot_conversation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_conversation ALTER COLUMN id SET DEFAULT nextval('public.chatbot_conversation_id_seq'::regclass);


--
-- Name: chatbot_message id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_message ALTER COLUMN id SET DEFAULT nextval('public.chatbot_message_id_seq'::regclass);


--
-- Name: checklist id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist ALTER COLUMN id SET DEFAULT nextval('public.checklist_id_seq'::regclass);


--
-- Name: checklist_answer id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_answer ALTER COLUMN id SET DEFAULT nextval('public.checklist_answer_id_seq'::regclass);


--
-- Name: checklist_generation_progress id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_generation_progress ALTER COLUMN id SET DEFAULT nextval('public.checklist_generation_progress_id_seq'::regclass);


--
-- Name: checklist_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_item ALTER COLUMN id SET DEFAULT nextval('public.checklist_item_id_seq'::regclass);


--
-- Name: fcm_token id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_token ALTER COLUMN id SET DEFAULT nextval('public.fcm_token_id_seq'::regclass);


--
-- Name: field_participant id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_participant ALTER COLUMN id SET DEFAULT nextval('public.field_participant_id_seq'::regclass);


--
-- Name: field_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_record ALTER COLUMN id SET DEFAULT nextval('public.field_record_id_seq'::regclass);


--
-- Name: field_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_session ALTER COLUMN id SET DEFAULT nextval('public.field_session_id_seq'::regclass);


--
-- Name: field_visit_candidate id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_candidate ALTER COLUMN id SET DEFAULT nextval('public.field_visit_candidate_id_seq'::regclass);


--
-- Name: field_visit_close_vote id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_close_vote ALTER COLUMN id SET DEFAULT nextval('public.field_visit_close_vote_id_seq'::regclass);


--
-- Name: field_visit_route id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route ALTER COLUMN id SET DEFAULT nextval('public.field_visit_route_id_seq'::regclass);


--
-- Name: field_visit_route_waypoint id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint ALTER COLUMN id SET DEFAULT nextval('public.field_visit_route_waypoint_id_seq'::regclass);


--
-- Name: field_visit_route_waypoint_item id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint_item ALTER COLUMN id SET DEFAULT nextval('public.field_visit_route_waypoint_item_id_seq'::regclass);


--
-- Name: field_visit_start_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_start_request ALTER COLUMN id SET DEFAULT nextval('public.field_visit_start_request_id_seq'::regclass);


--
-- Name: file_meta id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_meta ALTER COLUMN id SET DEFAULT nextval('public.file_meta_id_seq'::regclass);


--
-- Name: follow id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follow ALTER COLUMN id SET DEFAULT nextval('public.follow_id_seq'::regclass);


--
-- Name: member id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member ALTER COLUMN id SET DEFAULT nextval('public.member_id_seq'::regclass);


--
-- Name: member_preference id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_preference ALTER COLUMN id SET DEFAULT nextval('public.member_preference_id_seq'::regclass);


--
-- Name: member_review id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review ALTER COLUMN id SET DEFAULT nextval('public.member_review_id_seq'::regclass);


--
-- Name: member_review_tag id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review_tag ALTER COLUMN id SET DEFAULT nextval('public.member_review_tag_id_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification ALTER COLUMN id SET DEFAULT nextval('public.notification_id_seq'::regclass);


--
-- Name: post id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post ALTER COLUMN id SET DEFAULT nextval('public.post_id_seq'::regclass);


--
-- Name: post_attachment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_attachment ALTER COLUMN id SET DEFAULT nextval('public.post_attachment_id_seq'::regclass);


--
-- Name: post_comment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_comment ALTER COLUMN id SET DEFAULT nextval('public.post_comment_id_seq'::regclass);


--
-- Name: post_like id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_like ALTER COLUMN id SET DEFAULT nextval('public.post_like_id_seq'::regclass);


--
-- Name: report id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report ALTER COLUMN id SET DEFAULT nextval('public.report_id_seq'::regclass);


--
-- Name: report_evidence id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_evidence ALTER COLUMN id SET DEFAULT nextval('public.report_evidence_id_seq'::regclass);


--
-- Name: report_favorite id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_favorite ALTER COLUMN id SET DEFAULT nextval('public.report_favorite_id_seq'::regclass);


--
-- Name: schedule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule ALTER COLUMN id SET DEFAULT nextval('public.schedule_id_seq'::regclass);


--
-- Name: social_account id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_account ALTER COLUMN id SET DEFAULT nextval('public.social_account_id_seq'::regclass);


--
-- Name: stt_audio_cleanup_job id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_audio_cleanup_job ALTER COLUMN id SET DEFAULT nextval('public.stt_audio_cleanup_job_id_seq'::regclass);


--
-- Name: stt_dispatch_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_dispatch_outbox ALTER COLUMN id SET DEFAULT nextval('public.stt_dispatch_outbox_id_seq'::regclass);


--
-- Name: stt_job id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job ALTER COLUMN id SET DEFAULT nextval('public.stt_job_id_seq'::regclass);


--
-- Name: stt_job_attempt id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job_attempt ALTER COLUMN id SET DEFAULT nextval('public.stt_job_attempt_id_seq'::regclass);


--
-- Name: study id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study ALTER COLUMN id SET DEFAULT nextval('public.study_id_seq'::regclass);


--
-- Name: study_application id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_application ALTER COLUMN id SET DEFAULT nextval('public.study_application_id_seq'::regclass);


--
-- Name: study_member id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_member ALTER COLUMN id SET DEFAULT nextval('public.study_member_id_seq'::regclass);


--
-- Name: study_notice id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_notice ALTER COLUMN id SET DEFAULT nextval('public.study_notice_id_seq'::regclass);


--
-- Data for Name: apartment; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.apartment (id, complex_code, name, address, district_code, district_name, dong_name, legal_dong_code, longitude, latitude, household_count, completion_year_month, parking_space_count, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: apartment_favorite; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.apartment_favorite (id, member_id, apartment_id, created_at) FROM stdin;
\.


--
-- Data for Name: apartment_image; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.apartment_image (apartment_id, object_key, sha256, source_slug, source_csv, match_method, match_confidence, created_at) FROM stdin;
\.


--
-- Data for Name: apartment_rag_document; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.apartment_rag_document (id, apartment_id, source_type, source_id, content, embedding, source_at, reindex_key, created_at) FROM stdin;
\.


--
-- Data for Name: apartment_transaction; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.apartment_transaction (id, apartment_id, deal_date, exclusive_area, price, floor, is_canceled, dedup_key, collected_at) FROM stdin;
\.


--
-- Data for Name: chat_message; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chat_message (id, study_id, sender_id, message_type, content, image_file_id, created_at, client_message_id) FROM stdin;
\.


--
-- Data for Name: chat_read_status; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chat_read_status (id, study_id, member_id, last_read_at) FROM stdin;
\.


--
-- Data for Name: chatbot_conversation; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chatbot_conversation (id, member_id, apartment_id, last_message_at, created_at) FROM stdin;
\.


--
-- Data for Name: chatbot_message; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chatbot_message (id, conversation_id, role, status, content, basis_type, basis_label, sources_json, fail_reason, completed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: checklist; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.checklist (id, session_id, member_id, is_fallback, generated_at) FROM stdin;
\.


--
-- Data for Name: checklist_answer; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.checklist_answer (id, checklist_item_id, is_completed, completed_at, updated_at) FROM stdin;
\.


--
-- Data for Name: checklist_generation_progress; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.checklist_generation_progress (id, session_id, member_id, attempt_id, status, stage, progress_rate, message, failure_message, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: checklist_item; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.checklist_item (id, checklist_id, category, title, subtitle, display_order, example) FROM stdin;
\.


--
-- Data for Name: fcm_token; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.fcm_token (id, member_id, token, device_id, updated_at) FROM stdin;
\.


--
-- Data for Name: field_participant; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_participant (id, session_id, member_id, status, started_at, ended_at, end_reason, stay_duration_sec) FROM stdin;
\.


--
-- Data for Name: field_record; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_record (id, session_id, checklist_item_id, author_id, source_type, text_content, photo_file_id, stt_status, client_request_id, deleted_at, created_at, updated_at, request_fingerprint) FROM stdin;
\.


--
-- Data for Name: field_session; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_session (id, study_id, status, started_at, ended_at, ended_by_id, end_reason) FROM stdin;
\.


--
-- Data for Name: field_visit_candidate; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_visit_candidate (id, session_id, member_id, created_at) FROM stdin;
\.


--
-- Data for Name: field_visit_close_vote; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_visit_close_vote (id, field_session_id, field_participant_id, created_at) FROM stdin;
\.


--
-- Data for Name: field_visit_route; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_visit_route (id, session_id, generated_by_id, total_distance_meters, estimated_duration_minutes, generated_at, geometry) FROM stdin;
\.


--
-- Data for Name: field_visit_route_waypoint; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_visit_route_waypoint (id, route_id, sequence, facility_type, name, address, latitude, longitude, kakao_place_id, distance_from_origin_m, distance_from_prev_m, walk_minutes_from_prev, stay_minutes, guide) FROM stdin;
\.


--
-- Data for Name: field_visit_route_waypoint_item; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_visit_route_waypoint_item (id, waypoint_id, checklist_item_id) FROM stdin;
\.


--
-- Data for Name: field_visit_start_request; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.field_visit_start_request (id, member_id, study_id, session_id, participant_id, client_request_id, request_fingerprint, created_at) FROM stdin;
\.


--
-- Data for Name: file_meta; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.file_meta (id, owner_id, study_id, file_usage, original_name, s3_key, content_type, size_bytes, upload_status, expires_at, deleted_at, created_at) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init schema	SQL	V1__init_schema.sql	1582821256	porting	2026-08-10 00:25:18.110077	431	t
2	2	add onboarding lifestyle preferences	SQL	V2__add_onboarding_lifestyle_preferences.sql	633431988	porting	2026-08-10 00:25:18.704231	3	t
3	3	remove report public sharing columns	SQL	V3__remove_report_public_sharing_columns.sql	377988415	porting	2026-08-10 00:25:18.748084	3	t
4	4	add chat message client message id	SQL	V4__add_chat_message_client_message_id.sql	-561732509	porting	2026-08-10 00:25:18.770019	3	t
5	5	add stt job tables	SQL	V5__add_stt_job_tables.sql	54688737	porting	2026-08-10 00:25:18.789707	8	t
6	6	enforce community post constraints	SQL	V6__enforce_community_post_constraints.sql	-1519833151	porting	2026-08-10 00:25:18.814314	5	t
7	7	add study notice soft delete	SQL	V7__add_study_notice_soft_delete.sql	1032832122	porting	2026-08-10 00:25:18.854855	3	t
8	8	add study recruitment closed at	SQL	V8__add_study_recruitment_closed_at.sql	1475159914	porting	2026-08-10 00:25:18.883521	2	t
9	9	add study canceled at	SQL	V9__add_study_canceled_at.sql	-1720317160	porting	2026-08-10 00:25:18.935773	2	t
10	10	add field record request fingerprint	SQL	V10__add_field_record_request_fingerprint.sql	-1591657059	porting	2026-08-10 00:25:18.988336	2	t
11	11	add field visit start request	SQL	V11__add_field_visit_start_request.sql	-433668906	porting	2026-08-10 00:25:19.055612	4	t
12	12	add field visit candidate	SQL	V12__add_field_visit_candidate.sql	-222924943	porting	2026-08-10 00:25:19.069875	5	t
13	13	add post comment list index	SQL	V13__add_post_comment_list_index.sql	-1638907887	porting	2026-08-10 00:25:19.099354	2	t
14	14	add stt reliability jobs	SQL	V14__add_stt_reliability_jobs.sql	467705264	porting	2026-08-10 00:25:19.136708	8	t
15	15	change rag embedding dimension	SQL	V15__change_rag_embedding_dimension.sql	628523690	porting	2026-08-10 00:25:19.20218	4	t
16	16	add report processing lease	SQL	V16__add_report_processing_lease.sql	-1492144061	porting	2026-08-10 00:25:19.233463	6	t
17	17	add report complete payload hash	SQL	V17__add_report_complete_payload_hash.sql	-69623991	porting	2026-08-10 00:25:19.270304	2	t
18	18	add field visit route	SQL	V18__add_field_visit_route.sql	-935686942	porting	2026-08-10 00:25:19.281801	6	t
19	19	add field visit walking path	SQL	V19__add_field_visit_walking_path.sql	206951851	porting	2026-08-10 00:25:19.306719	2	t
20	20	enforce auto report post uniqueness	SQL	V20__enforce_auto_report_post_uniqueness.sql	711330593	porting	2026-08-10 00:25:19.343129	3	t
21	21	add report failure metadata	SQL	V21__add_report_failure_metadata.sql	-1450080894	porting	2026-08-10 00:25:19.420393	2	t
22	22	add report retry requested at	SQL	V22__add_report_retry_requested_at.sql	-790030730	porting	2026-08-10 00:25:19.467138	2	t
23	23	add apartment images	SQL	V23__add_apartment_images.sql	-1803789708	porting	2026-08-10 00:25:19.481432	21	t
24	24	add oksu apartment images	SQL	V24__add_oksu_apartment_images.sql	-218670390	porting	2026-08-10 00:25:19.67137	2	t
25	25	add field visit close vote	SQL	V25__add_field_visit_close_vote.sql	515900807	porting	2026-08-10 00:25:19.70512	3	t
26	26	add notification priority paging index	SQL	V26__add_notification_priority_paging_index.sql	1142245299	porting	2026-08-10 00:25:19.756392	2	t
27	27	publish transparent apartment images	SQL	V27__publish_transparent_apartment_images.sql	117757422	porting	2026-08-10 00:25:19.794177	10	t
28	28	add member review	SQL	V28__add_member_review.sql	1154327759	porting	2026-08-10 00:25:19.90212	3	t
29	29	add checklist generation progress	SQL	V29__add_checklist_generation_progress.sql	-992823466	porting	2026-08-10 00:25:19.969922	4	t
30	30	add study chat push enabled	SQL	V30__add_study_chat_push_enabled.sql	1658102061	porting	2026-08-10 00:25:19.991663	2	t
31	31	add checklist item example	SQL	V31__add_checklist_item_example.sql	-798357491	porting	2026-08-10 00:25:20.042435	2	t
32	32	member review tags and likes	SQL	V32__member_review_tags_and_likes.sql	-888362652	porting	2026-08-10 00:25:20.112317	3	t
33	33	republish apartment images	SQL	V33__republish_apartment_images.sql	-1295082820	porting	2026-08-10 00:25:20.12711	8	t
34	34	restyle apartment images v4	SQL	V34__restyle_apartment_images_v4.sql	1238080424	porting	2026-08-10 00:25:20.152361	4	t
\.


--
-- Data for Name: follow; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.follow (id, follower_id, following_id, created_at) FROM stdin;
\.


--
-- Data for Name: member; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.member (id, email, password_hash, nickname, profile_image_url, selected_character_id, age_group, age_group_public_agreed, service_notification_agreed, ad_notification_agreed, status, deleted_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: member_preference; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.member_preference (id, member_id, purpose, household_type, budget, interest_region, interest_region_public_agreed, priorities, created_at, updated_at, marital_status, has_vehicle, has_children) FROM stdin;
\.


--
-- Data for Name: member_review; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.member_review (id, study_id, reviewer_id, reviewee_id, rating, content, created_at, liked) FROM stdin;
\.


--
-- Data for Name: member_review_tag; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.member_review_tag (id, review_id, tag_code) FROM stdin;
\.


--
-- Data for Name: notification; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification (id, recipient_id, actor_id, category, type, target_screen, target_id, target_sub_id, title, body, send_status, fail_reason, is_read, read_at, idempotency_key, sent_at) FROM stdin;
\.


--
-- Data for Name: post; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.post (id, board_type, author_id, title, content, status, is_auto_report, report_id, apartment_id, view_count, deleted_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: post_attachment; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.post_attachment (id, post_id, file_id, display_order, created_at) FROM stdin;
\.


--
-- Data for Name: post_comment; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.post_comment (id, post_id, author_id, content, deleted_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: post_like; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.post_like (id, post_id, member_id, created_at) FROM stdin;
\.


--
-- Data for Name: report; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.report (id, study_id, apartment_id, status, progress_stage, result_json, fail_reason, is_retryable, published_at, completed_at, created_at, updated_at, field_session_id, processing_token_hash, processing_attempt, processing_lease_expires_at, complete_payload_hash, fail_code, failed_at, fail_payload_hash, retry_requested_at) FROM stdin;
\.


--
-- Data for Name: report_evidence; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.report_evidence (id, report_id, field_record_id, claim_key, display_order) FROM stdin;
\.


--
-- Data for Name: report_favorite; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.report_favorite (id, member_id, report_id, created_at) FROM stdin;
\.


--
-- Data for Name: schedule; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.schedule (id, study_id, start_at, end_at, meeting_place, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: social_account; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.social_account (id, member_id, provider, social_user_id, email, created_at) FROM stdin;
\.


--
-- Data for Name: spatial_ref_sys; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.spatial_ref_sys (srid, auth_name, auth_srid, srtext, proj4text) FROM stdin;
\.


--
-- Data for Name: stt_audio_cleanup_job; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.stt_audio_cleanup_job (id, audio_file_id, object_key, status, attempt_count, next_attempt_at, last_error, completed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: stt_dispatch_outbox; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.stt_dispatch_outbox (id, stt_job_id, stt_id, attempt_no, audio_file_id, object_key, content_type, language, status, attempt_count, next_attempt_at, last_error, published_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: stt_job; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.stt_job (id, stt_id, member_id, study_id, session_id, audio_file_id, checklist_item_id, field_record_id, initial_client_request_id, status, fail_code, fail_reason, retryable, retry_count, requested_at, started_at, completed_at, last_dispatched_at, dispatch_count, version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: stt_job_attempt; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.stt_job_attempt (id, stt_job_id, member_id, client_request_id, attempt_no, request_type, status, fail_code, fail_reason, requested_at, started_at, finished_at) FROM stdin;
\.


--
-- Data for Name: study; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.study (id, apartment_id, leader_id, title, intro, goal, capacity, purpose, status, deleted_at, created_at, updated_at, recruitment_closed_at, canceled_at) FROM stdin;
\.


--
-- Data for Name: study_application; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.study_application (id, study_id, applicant_id, intro, purpose, status, created_at, decided_at) FROM stdin;
\.


--
-- Data for Name: study_member; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.study_member (id, study_id, member_id, role, status, joined_at, left_at, chat_push_enabled) FROM stdin;
\.


--
-- Data for Name: study_notice; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.study_notice (id, study_id, content, created_at, updated_at, deleted_at) FROM stdin;
\.


--
-- Name: apartment_favorite_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.apartment_favorite_id_seq', 1, false);


--
-- Name: apartment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.apartment_id_seq', 1, false);


--
-- Name: apartment_rag_document_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.apartment_rag_document_id_seq', 1, false);


--
-- Name: apartment_transaction_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.apartment_transaction_id_seq', 1, false);


--
-- Name: chat_message_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.chat_message_id_seq', 1, false);


--
-- Name: chat_read_status_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.chat_read_status_id_seq', 1, false);


--
-- Name: chatbot_conversation_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.chatbot_conversation_id_seq', 1, false);


--
-- Name: chatbot_message_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.chatbot_message_id_seq', 1, false);


--
-- Name: checklist_answer_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.checklist_answer_id_seq', 1, false);


--
-- Name: checklist_generation_progress_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.checklist_generation_progress_id_seq', 1, false);


--
-- Name: checklist_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.checklist_id_seq', 1, false);


--
-- Name: checklist_item_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.checklist_item_id_seq', 1, false);


--
-- Name: fcm_token_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.fcm_token_id_seq', 1, false);


--
-- Name: field_participant_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_participant_id_seq', 1, false);


--
-- Name: field_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_record_id_seq', 1, false);


--
-- Name: field_session_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_session_id_seq', 1, false);


--
-- Name: field_visit_candidate_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_visit_candidate_id_seq', 1, false);


--
-- Name: field_visit_close_vote_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_visit_close_vote_id_seq', 1, false);


--
-- Name: field_visit_route_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_visit_route_id_seq', 1, false);


--
-- Name: field_visit_route_waypoint_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_visit_route_waypoint_id_seq', 1, false);


--
-- Name: field_visit_route_waypoint_item_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_visit_route_waypoint_item_id_seq', 1, false);


--
-- Name: field_visit_start_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.field_visit_start_request_id_seq', 1, false);


--
-- Name: file_meta_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.file_meta_id_seq', 1, false);


--
-- Name: follow_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.follow_id_seq', 1, false);


--
-- Name: member_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.member_id_seq', 1, false);


--
-- Name: member_preference_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.member_preference_id_seq', 1, false);


--
-- Name: member_review_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.member_review_id_seq', 1, false);


--
-- Name: member_review_tag_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.member_review_tag_id_seq', 1, false);


--
-- Name: notification_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_id_seq', 1, false);


--
-- Name: post_attachment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.post_attachment_id_seq', 1, false);


--
-- Name: post_comment_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.post_comment_id_seq', 1, false);


--
-- Name: post_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.post_id_seq', 1, false);


--
-- Name: post_like_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.post_like_id_seq', 1, false);


--
-- Name: report_evidence_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.report_evidence_id_seq', 1, false);


--
-- Name: report_favorite_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.report_favorite_id_seq', 1, false);


--
-- Name: report_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.report_id_seq', 1, false);


--
-- Name: schedule_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.schedule_id_seq', 1, false);


--
-- Name: social_account_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.social_account_id_seq', 1, false);


--
-- Name: stt_audio_cleanup_job_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.stt_audio_cleanup_job_id_seq', 1, false);


--
-- Name: stt_dispatch_outbox_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.stt_dispatch_outbox_id_seq', 1, false);


--
-- Name: stt_job_attempt_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.stt_job_attempt_id_seq', 1, false);


--
-- Name: stt_job_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.stt_job_id_seq', 1, false);


--
-- Name: study_application_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.study_application_id_seq', 1, false);


--
-- Name: study_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.study_id_seq', 1, false);


--
-- Name: study_member_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.study_member_id_seq', 1, false);


--
-- Name: study_notice_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.study_notice_id_seq', 1, false);


--
-- Name: apartment apartment_complex_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment
    ADD CONSTRAINT apartment_complex_code_key UNIQUE (complex_code);


--
-- Name: apartment_favorite apartment_favorite_member_id_apartment_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_favorite
    ADD CONSTRAINT apartment_favorite_member_id_apartment_id_key UNIQUE (member_id, apartment_id);


--
-- Name: apartment_favorite apartment_favorite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_favorite
    ADD CONSTRAINT apartment_favorite_pkey PRIMARY KEY (id);


--
-- Name: apartment_image apartment_image_object_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_image
    ADD CONSTRAINT apartment_image_object_key_key UNIQUE (object_key);


--
-- Name: apartment_image apartment_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_image
    ADD CONSTRAINT apartment_image_pkey PRIMARY KEY (apartment_id);


--
-- Name: apartment apartment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment
    ADD CONSTRAINT apartment_pkey PRIMARY KEY (id);


--
-- Name: apartment_rag_document apartment_rag_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_rag_document
    ADD CONSTRAINT apartment_rag_document_pkey PRIMARY KEY (id);


--
-- Name: apartment_rag_document apartment_rag_document_reindex_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_rag_document
    ADD CONSTRAINT apartment_rag_document_reindex_key_key UNIQUE (reindex_key);


--
-- Name: apartment_transaction apartment_transaction_dedup_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_transaction
    ADD CONSTRAINT apartment_transaction_dedup_key_key UNIQUE (dedup_key);


--
-- Name: apartment_transaction apartment_transaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_transaction
    ADD CONSTRAINT apartment_transaction_pkey PRIMARY KEY (id);


--
-- Name: chat_message chat_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message
    ADD CONSTRAINT chat_message_pkey PRIMARY KEY (id);


--
-- Name: chat_read_status chat_read_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_read_status
    ADD CONSTRAINT chat_read_status_pkey PRIMARY KEY (id);


--
-- Name: chat_read_status chat_read_status_study_id_member_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_read_status
    ADD CONSTRAINT chat_read_status_study_id_member_id_key UNIQUE (study_id, member_id);


--
-- Name: chatbot_conversation chatbot_conversation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_conversation
    ADD CONSTRAINT chatbot_conversation_pkey PRIMARY KEY (id);


--
-- Name: chatbot_message chatbot_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_message
    ADD CONSTRAINT chatbot_message_pkey PRIMARY KEY (id);


--
-- Name: checklist_answer checklist_answer_checklist_item_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_answer
    ADD CONSTRAINT checklist_answer_checklist_item_id_key UNIQUE (checklist_item_id);


--
-- Name: checklist_answer checklist_answer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_answer
    ADD CONSTRAINT checklist_answer_pkey PRIMARY KEY (id);


--
-- Name: checklist_generation_progress checklist_generation_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_generation_progress
    ADD CONSTRAINT checklist_generation_progress_pkey PRIMARY KEY (id);


--
-- Name: checklist_generation_progress checklist_generation_progress_session_id_member_id_attempt__key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_generation_progress
    ADD CONSTRAINT checklist_generation_progress_session_id_member_id_attempt__key UNIQUE (session_id, member_id, attempt_id);


--
-- Name: checklist_item checklist_item_checklist_id_display_order_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_item
    ADD CONSTRAINT checklist_item_checklist_id_display_order_key UNIQUE (checklist_id, display_order);


--
-- Name: checklist_item checklist_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_item
    ADD CONSTRAINT checklist_item_pkey PRIMARY KEY (id);


--
-- Name: checklist checklist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist
    ADD CONSTRAINT checklist_pkey PRIMARY KEY (id);


--
-- Name: checklist checklist_session_id_member_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist
    ADD CONSTRAINT checklist_session_id_member_id_key UNIQUE (session_id, member_id);


--
-- Name: fcm_token fcm_token_member_id_device_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_token
    ADD CONSTRAINT fcm_token_member_id_device_id_key UNIQUE (member_id, device_id);


--
-- Name: fcm_token fcm_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_token
    ADD CONSTRAINT fcm_token_pkey PRIMARY KEY (id);


--
-- Name: fcm_token fcm_token_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_token
    ADD CONSTRAINT fcm_token_token_key UNIQUE (token);


--
-- Name: field_participant field_participant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_participant
    ADD CONSTRAINT field_participant_pkey PRIMARY KEY (id);


--
-- Name: field_participant field_participant_session_id_member_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_participant
    ADD CONSTRAINT field_participant_session_id_member_id_key UNIQUE (session_id, member_id);


--
-- Name: field_record field_record_client_request_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_record
    ADD CONSTRAINT field_record_client_request_id_key UNIQUE (client_request_id);


--
-- Name: field_record field_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_record
    ADD CONSTRAINT field_record_pkey PRIMARY KEY (id);


--
-- Name: field_session field_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_session
    ADD CONSTRAINT field_session_pkey PRIMARY KEY (id);


--
-- Name: field_session field_session_study_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_session
    ADD CONSTRAINT field_session_study_id_key UNIQUE (study_id);


--
-- Name: field_visit_candidate field_visit_candidate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_candidate
    ADD CONSTRAINT field_visit_candidate_pkey PRIMARY KEY (id);


--
-- Name: field_visit_close_vote field_visit_close_vote_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_close_vote
    ADD CONSTRAINT field_visit_close_vote_pkey PRIMARY KEY (id);


--
-- Name: field_visit_route field_visit_route_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route
    ADD CONSTRAINT field_visit_route_pkey PRIMARY KEY (id);


--
-- Name: field_visit_route field_visit_route_session_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route
    ADD CONSTRAINT field_visit_route_session_id_key UNIQUE (session_id);


--
-- Name: field_visit_route_waypoint_item field_visit_route_waypoint_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint_item
    ADD CONSTRAINT field_visit_route_waypoint_item_pkey PRIMARY KEY (id);


--
-- Name: field_visit_route_waypoint field_visit_route_waypoint_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint
    ADD CONSTRAINT field_visit_route_waypoint_pkey PRIMARY KEY (id);


--
-- Name: field_visit_start_request field_visit_start_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_start_request
    ADD CONSTRAINT field_visit_start_request_pkey PRIMARY KEY (id);


--
-- Name: file_meta file_meta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_meta_pkey PRIMARY KEY (id);


--
-- Name: file_meta file_meta_s3_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_meta_s3_key_key UNIQUE (s3_key);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: follow follow_follower_id_following_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follow
    ADD CONSTRAINT follow_follower_id_following_id_key UNIQUE (follower_id, following_id);


--
-- Name: follow follow_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follow
    ADD CONSTRAINT follow_pkey PRIMARY KEY (id);


--
-- Name: member member_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member
    ADD CONSTRAINT member_email_key UNIQUE (email);


--
-- Name: member member_nickname_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member
    ADD CONSTRAINT member_nickname_key UNIQUE (nickname);


--
-- Name: member member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member
    ADD CONSTRAINT member_pkey PRIMARY KEY (id);


--
-- Name: member_preference member_preference_member_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_preference
    ADD CONSTRAINT member_preference_member_id_key UNIQUE (member_id);


--
-- Name: member_preference member_preference_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_preference
    ADD CONSTRAINT member_preference_pkey PRIMARY KEY (id);


--
-- Name: member_review member_review_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review
    ADD CONSTRAINT member_review_pkey PRIMARY KEY (id);


--
-- Name: member_review_tag member_review_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review_tag
    ADD CONSTRAINT member_review_tag_pkey PRIMARY KEY (id);


--
-- Name: notification notification_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: post_attachment post_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_attachment
    ADD CONSTRAINT post_attachment_pkey PRIMARY KEY (id);


--
-- Name: post_attachment post_attachment_post_id_display_order_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_attachment
    ADD CONSTRAINT post_attachment_post_id_display_order_key UNIQUE (post_id, display_order);


--
-- Name: post_attachment post_attachment_post_id_file_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_attachment
    ADD CONSTRAINT post_attachment_post_id_file_id_key UNIQUE (post_id, file_id);


--
-- Name: post_comment post_comment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_comment
    ADD CONSTRAINT post_comment_pkey PRIMARY KEY (id);


--
-- Name: post_like post_like_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_like
    ADD CONSTRAINT post_like_pkey PRIMARY KEY (id);


--
-- Name: post_like post_like_post_id_member_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_like
    ADD CONSTRAINT post_like_post_id_member_id_key UNIQUE (post_id, member_id);


--
-- Name: post post_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post
    ADD CONSTRAINT post_pkey PRIMARY KEY (id);


--
-- Name: report_evidence report_evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_evidence
    ADD CONSTRAINT report_evidence_pkey PRIMARY KEY (id);


--
-- Name: report_evidence report_evidence_report_id_field_record_id_claim_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_evidence
    ADD CONSTRAINT report_evidence_report_id_field_record_id_claim_key_key UNIQUE (report_id, field_record_id, claim_key);


--
-- Name: report_favorite report_favorite_member_id_report_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_favorite
    ADD CONSTRAINT report_favorite_member_id_report_id_key UNIQUE (member_id, report_id);


--
-- Name: report_favorite report_favorite_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_favorite
    ADD CONSTRAINT report_favorite_pkey PRIMARY KEY (id);


--
-- Name: report report_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_pkey PRIMARY KEY (id);


--
-- Name: report report_study_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_study_id_key UNIQUE (study_id);


--
-- Name: schedule schedule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule
    ADD CONSTRAINT schedule_pkey PRIMARY KEY (id);


--
-- Name: schedule schedule_study_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule
    ADD CONSTRAINT schedule_study_id_key UNIQUE (study_id);


--
-- Name: social_account social_account_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_account
    ADD CONSTRAINT social_account_pkey PRIMARY KEY (id);


--
-- Name: social_account social_account_provider_social_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_account
    ADD CONSTRAINT social_account_provider_social_user_id_key UNIQUE (provider, social_user_id);


--
-- Name: stt_audio_cleanup_job stt_audio_cleanup_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_audio_cleanup_job
    ADD CONSTRAINT stt_audio_cleanup_job_pkey PRIMARY KEY (id);


--
-- Name: stt_dispatch_outbox stt_dispatch_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_dispatch_outbox
    ADD CONSTRAINT stt_dispatch_outbox_pkey PRIMARY KEY (id);


--
-- Name: stt_job_attempt stt_job_attempt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job_attempt
    ADD CONSTRAINT stt_job_attempt_pkey PRIMARY KEY (id);


--
-- Name: stt_job stt_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT stt_job_pkey PRIMARY KEY (id);


--
-- Name: study_application study_application_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_application
    ADD CONSTRAINT study_application_pkey PRIMARY KEY (id);


--
-- Name: study_application study_application_study_id_applicant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_application
    ADD CONSTRAINT study_application_study_id_applicant_id_key UNIQUE (study_id, applicant_id);


--
-- Name: study_member study_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_member
    ADD CONSTRAINT study_member_pkey PRIMARY KEY (id);


--
-- Name: study_member study_member_study_id_member_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_member
    ADD CONSTRAINT study_member_study_id_member_id_key UNIQUE (study_id, member_id);


--
-- Name: study_notice study_notice_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_notice
    ADD CONSTRAINT study_notice_pkey PRIMARY KEY (id);


--
-- Name: study study_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study
    ADD CONSTRAINT study_pkey PRIMARY KEY (id);


--
-- Name: field_visit_candidate uk_field_visit_candidate_session_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_candidate
    ADD CONSTRAINT uk_field_visit_candidate_session_member UNIQUE (session_id, member_id);


--
-- Name: field_visit_start_request uk_field_visit_start_request_member_client; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_start_request
    ADD CONSTRAINT uk_field_visit_start_request_member_client UNIQUE (member_id, client_request_id);


--
-- Name: field_visit_route_waypoint_item uk_route_waypoint_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint_item
    ADD CONSTRAINT uk_route_waypoint_item UNIQUE (waypoint_id, checklist_item_id);


--
-- Name: field_visit_route_waypoint uk_route_waypoint_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint
    ADD CONSTRAINT uk_route_waypoint_sequence UNIQUE (route_id, sequence);


--
-- Name: stt_audio_cleanup_job uk_stt_audio_cleanup_file; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_audio_cleanup_job
    ADD CONSTRAINT uk_stt_audio_cleanup_file UNIQUE (audio_file_id);


--
-- Name: stt_dispatch_outbox uk_stt_dispatch_outbox_attempt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_dispatch_outbox
    ADD CONSTRAINT uk_stt_dispatch_outbox_attempt UNIQUE (stt_id, attempt_no);


--
-- Name: stt_job_attempt uk_stt_job_attempt_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job_attempt
    ADD CONSTRAINT uk_stt_job_attempt_number UNIQUE (stt_job_id, attempt_no);


--
-- Name: stt_job_attempt uk_stt_job_attempt_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job_attempt
    ADD CONSTRAINT uk_stt_job_attempt_request UNIQUE (member_id, client_request_id);


--
-- Name: stt_job uk_stt_job_audio_file; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT uk_stt_job_audio_file UNIQUE (audio_file_id);


--
-- Name: stt_job uk_stt_job_field_record; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT uk_stt_job_field_record UNIQUE (field_record_id);


--
-- Name: stt_job uk_stt_job_initial_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT uk_stt_job_initial_request UNIQUE (member_id, initial_client_request_id);


--
-- Name: stt_job uk_stt_job_stt_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT uk_stt_job_stt_id UNIQUE (stt_id);


--
-- Name: field_visit_close_vote uq_field_visit_close_vote_session_participant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_close_vote
    ADD CONSTRAINT uq_field_visit_close_vote_session_participant UNIQUE (field_session_id, field_participant_id);


--
-- Name: member_review uq_member_review_study_reviewer_reviewee; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review
    ADD CONSTRAINT uq_member_review_study_reviewer_reviewee UNIQUE (study_id, reviewer_id, reviewee_id);


--
-- Name: member_review_tag uq_member_review_tag; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review_tag
    ADD CONSTRAINT uq_member_review_tag UNIQUE (review_id, tag_code);


--
-- Name: report uq_report_field_session; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report
    ADD CONSTRAINT uq_report_field_session UNIQUE (field_session_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_apartment_address_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_address_trgm ON public.apartment USING gin (address public.gin_trgm_ops);


--
-- Name: idx_apartment_coord; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_coord ON public.apartment USING btree (latitude, longitude);


--
-- Name: idx_apartment_district; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_district ON public.apartment USING btree (district_code, district_name);


--
-- Name: idx_apartment_dong; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_dong ON public.apartment USING btree (district_code, dong_name);


--
-- Name: idx_apartment_dong_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_dong_trgm ON public.apartment USING gin (dong_name public.gin_trgm_ops);


--
-- Name: idx_apartment_favorite_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_favorite_member ON public.apartment_favorite USING btree (member_id, id DESC);


--
-- Name: idx_apartment_location_gist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_location_gist ON public.apartment USING gist (((public.st_setsrid(public.st_makepoint(longitude, latitude), 4326))::public.geography));


--
-- Name: idx_apartment_name_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_apartment_name_trgm ON public.apartment USING gin (name public.gin_trgm_ops);


--
-- Name: idx_chat_message_study; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_message_study ON public.chat_message USING btree (study_id, id DESC);


--
-- Name: idx_chatbot_conversation_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_conversation_member ON public.chatbot_conversation USING btree (member_id, last_message_at DESC);


--
-- Name: idx_chatbot_message_conv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_message_conv ON public.chatbot_message USING btree (conversation_id, id);


--
-- Name: idx_chatbot_message_processing; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatbot_message_processing ON public.chatbot_message USING btree (status, created_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::text[]));


--
-- Name: idx_field_participant_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_participant_member ON public.field_participant USING btree (member_id, session_id);


--
-- Name: idx_field_record_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_record_author ON public.field_record USING btree (author_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_field_record_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_record_item ON public.field_record USING btree (checklist_item_id, created_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_field_visit_close_vote_session_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_visit_close_vote_session_id ON public.field_visit_close_vote USING btree (field_session_id);


--
-- Name: idx_field_visit_start_request_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_field_visit_start_request_session ON public.field_visit_start_request USING btree (session_id);


--
-- Name: idx_file_meta_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_file_meta_expiry ON public.file_meta USING btree (expires_at) WHERE ((deleted_at IS NULL) AND (expires_at IS NOT NULL));


--
-- Name: idx_follow_follower; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_follow_follower ON public.follow USING btree (follower_id, id DESC);


--
-- Name: idx_member_review_reviewee_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_member_review_reviewee_created ON public.member_review USING btree (reviewee_id, created_at DESC, id DESC);


--
-- Name: idx_member_review_tag_review; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_member_review_tag_review ON public.member_review_tag USING btree (review_id);


--
-- Name: idx_notification_recipient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_recipient ON public.notification USING btree (recipient_id, id DESC);


--
-- Name: idx_notification_recipient_read_sent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_recipient_read_sent ON public.notification USING btree (recipient_id, is_read, sent_at DESC, id DESC);


--
-- Name: idx_notification_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_target ON public.notification USING btree (category, target_screen, target_id, target_sub_id);


--
-- Name: idx_post_attachment_post; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_attachment_post ON public.post_attachment USING btree (post_id, display_order);


--
-- Name: idx_post_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_author ON public.post USING btree (author_id, id DESC);


--
-- Name: idx_post_board_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_board_recent ON public.post USING btree (board_type, status, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_post_comment_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_comment_author ON public.post_comment USING btree (author_id, id DESC);


--
-- Name: idx_post_comment_post; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_comment_post ON public.post_comment USING btree (post_id, id);


--
-- Name: idx_post_comment_post_created_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_comment_post_created_active ON public.post_comment USING btree (post_id, created_at, id) WHERE (deleted_at IS NULL);


--
-- Name: idx_post_hot_candidates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_hot_candidates ON public.post USING btree (board_type, status, view_count DESC, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_post_like_post; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_like_post ON public.post_like USING btree (post_id, id);


--
-- Name: idx_post_search_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_search_trgm ON public.post USING gin (((((title)::text || ' '::text) || COALESCE(content, ''::text))) public.gin_trgm_ops);


--
-- Name: idx_report_apartment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_apartment_status ON public.report USING btree (apartment_id, status, completed_at DESC);


--
-- Name: idx_report_evidence_report; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_evidence_report ON public.report_evidence USING btree (report_id, display_order, id);


--
-- Name: idx_report_favorite_member; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_favorite_member ON public.report_favorite USING btree (member_id, id DESC);


--
-- Name: idx_route_waypoint_item_checklist_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_route_waypoint_item_checklist_item ON public.field_visit_route_waypoint_item USING btree (checklist_item_id);


--
-- Name: idx_schedule_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_schedule_start ON public.schedule USING btree (start_at, status);


--
-- Name: idx_stt_audio_cleanup_ready; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stt_audio_cleanup_ready ON public.stt_audio_cleanup_job USING btree (status, next_attempt_at, id);


--
-- Name: idx_stt_dispatch_outbox_ready; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stt_dispatch_outbox_ready ON public.stt_dispatch_outbox USING btree (status, next_attempt_at, id);


--
-- Name: idx_stt_job_attempt_job; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stt_job_attempt_job ON public.stt_job_attempt USING btree (stt_job_id, attempt_no);


--
-- Name: idx_stt_job_member_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stt_job_member_status ON public.stt_job USING btree (member_id, status);


--
-- Name: idx_stt_job_study_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stt_job_study_status ON public.stt_job USING btree (study_id, status);


--
-- Name: idx_study_apartment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_apartment_status ON public.study USING btree (apartment_id, status, id DESC);


--
-- Name: idx_study_member_member_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_member_member_status ON public.study_member USING btree (member_id, status, study_id);


--
-- Name: idx_study_notice_active_study_id_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_study_notice_active_study_id_id ON public.study_notice USING btree (study_id, id DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_transaction_recent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaction_recent ON public.apartment_transaction USING btree (apartment_id, deal_date DESC);


--
-- Name: uq_chat_message_sender_client_message; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_chat_message_sender_client_message ON public.chat_message USING btree (sender_id, client_message_id) WHERE ((sender_id IS NOT NULL) AND (client_message_id IS NOT NULL));


--
-- Name: uq_post_attachment_file; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_post_attachment_file ON public.post_attachment USING btree (file_id);


--
-- Name: uq_post_auto_report_per_report; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_post_auto_report_per_report ON public.post USING btree (report_id) WHERE ((is_auto_report = true) AND (report_id IS NOT NULL));


--
-- Name: apartment_favorite apartment_favorite_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_favorite
    ADD CONSTRAINT apartment_favorite_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: apartment_favorite apartment_favorite_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_favorite
    ADD CONSTRAINT apartment_favorite_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: apartment_image apartment_image_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_image
    ADD CONSTRAINT apartment_image_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: apartment_rag_document apartment_rag_document_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_rag_document
    ADD CONSTRAINT apartment_rag_document_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: apartment_transaction apartment_transaction_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apartment_transaction
    ADD CONSTRAINT apartment_transaction_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: chat_message chat_message_image_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message
    ADD CONSTRAINT chat_message_image_file_id_fkey FOREIGN KEY (image_file_id) REFERENCES public.file_meta(id);


--
-- Name: chat_message chat_message_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message
    ADD CONSTRAINT chat_message_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.member(id);


--
-- Name: chat_message chat_message_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_message
    ADD CONSTRAINT chat_message_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: chat_read_status chat_read_status_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_read_status
    ADD CONSTRAINT chat_read_status_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: chat_read_status chat_read_status_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_read_status
    ADD CONSTRAINT chat_read_status_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: chatbot_conversation chatbot_conversation_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_conversation
    ADD CONSTRAINT chatbot_conversation_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: chatbot_conversation chatbot_conversation_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_conversation
    ADD CONSTRAINT chatbot_conversation_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: chatbot_message chatbot_message_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chatbot_message
    ADD CONSTRAINT chatbot_message_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.chatbot_conversation(id);


--
-- Name: checklist_answer checklist_answer_checklist_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_answer
    ADD CONSTRAINT checklist_answer_checklist_item_id_fkey FOREIGN KEY (checklist_item_id) REFERENCES public.checklist_item(id);


--
-- Name: checklist_generation_progress checklist_generation_progress_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_generation_progress
    ADD CONSTRAINT checklist_generation_progress_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: checklist_generation_progress checklist_generation_progress_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_generation_progress
    ADD CONSTRAINT checklist_generation_progress_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: checklist_item checklist_item_checklist_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist_item
    ADD CONSTRAINT checklist_item_checklist_id_fkey FOREIGN KEY (checklist_id) REFERENCES public.checklist(id);


--
-- Name: checklist checklist_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist
    ADD CONSTRAINT checklist_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: checklist checklist_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.checklist
    ADD CONSTRAINT checklist_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: fcm_token fcm_token_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_token
    ADD CONSTRAINT fcm_token_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: field_participant field_participant_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_participant
    ADD CONSTRAINT field_participant_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: field_participant field_participant_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_participant
    ADD CONSTRAINT field_participant_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: field_record field_record_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_record
    ADD CONSTRAINT field_record_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.member(id);


--
-- Name: field_record field_record_checklist_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_record
    ADD CONSTRAINT field_record_checklist_item_id_fkey FOREIGN KEY (checklist_item_id) REFERENCES public.checklist_item(id);


--
-- Name: field_record field_record_photo_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_record
    ADD CONSTRAINT field_record_photo_file_id_fkey FOREIGN KEY (photo_file_id) REFERENCES public.file_meta(id);


--
-- Name: field_record field_record_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_record
    ADD CONSTRAINT field_record_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: field_session field_session_ended_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_session
    ADD CONSTRAINT field_session_ended_by_id_fkey FOREIGN KEY (ended_by_id) REFERENCES public.member(id);


--
-- Name: field_session field_session_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_session
    ADD CONSTRAINT field_session_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: field_visit_candidate field_visit_candidate_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_candidate
    ADD CONSTRAINT field_visit_candidate_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: field_visit_candidate field_visit_candidate_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_candidate
    ADD CONSTRAINT field_visit_candidate_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: field_visit_close_vote field_visit_close_vote_field_participant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_close_vote
    ADD CONSTRAINT field_visit_close_vote_field_participant_id_fkey FOREIGN KEY (field_participant_id) REFERENCES public.field_participant(id);


--
-- Name: field_visit_close_vote field_visit_close_vote_field_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_close_vote
    ADD CONSTRAINT field_visit_close_vote_field_session_id_fkey FOREIGN KEY (field_session_id) REFERENCES public.field_session(id);


--
-- Name: field_visit_route field_visit_route_generated_by_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route
    ADD CONSTRAINT field_visit_route_generated_by_id_fkey FOREIGN KEY (generated_by_id) REFERENCES public.member(id);


--
-- Name: field_visit_route field_visit_route_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route
    ADD CONSTRAINT field_visit_route_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: field_visit_route_waypoint_item field_visit_route_waypoint_item_checklist_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint_item
    ADD CONSTRAINT field_visit_route_waypoint_item_checklist_item_id_fkey FOREIGN KEY (checklist_item_id) REFERENCES public.checklist_item(id) ON DELETE CASCADE;


--
-- Name: field_visit_route_waypoint_item field_visit_route_waypoint_item_waypoint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint_item
    ADD CONSTRAINT field_visit_route_waypoint_item_waypoint_id_fkey FOREIGN KEY (waypoint_id) REFERENCES public.field_visit_route_waypoint(id) ON DELETE CASCADE;


--
-- Name: field_visit_route_waypoint field_visit_route_waypoint_route_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_route_waypoint
    ADD CONSTRAINT field_visit_route_waypoint_route_id_fkey FOREIGN KEY (route_id) REFERENCES public.field_visit_route(id) ON DELETE CASCADE;


--
-- Name: field_visit_start_request field_visit_start_request_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_start_request
    ADD CONSTRAINT field_visit_start_request_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: field_visit_start_request field_visit_start_request_participant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_start_request
    ADD CONSTRAINT field_visit_start_request_participant_id_fkey FOREIGN KEY (participant_id) REFERENCES public.field_participant(id);


--
-- Name: field_visit_start_request field_visit_start_request_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_start_request
    ADD CONSTRAINT field_visit_start_request_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: field_visit_start_request field_visit_start_request_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.field_visit_start_request
    ADD CONSTRAINT field_visit_start_request_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: file_meta file_meta_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_meta_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.member(id);


--
-- Name: file_meta file_meta_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_meta
    ADD CONSTRAINT file_meta_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: report fk_report_field_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report
    ADD CONSTRAINT fk_report_field_session FOREIGN KEY (field_session_id) REFERENCES public.field_session(id);


--
-- Name: follow follow_follower_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follow
    ADD CONSTRAINT follow_follower_id_fkey FOREIGN KEY (follower_id) REFERENCES public.member(id);


--
-- Name: follow follow_following_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follow
    ADD CONSTRAINT follow_following_id_fkey FOREIGN KEY (following_id) REFERENCES public.member(id);


--
-- Name: member_preference member_preference_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_preference
    ADD CONSTRAINT member_preference_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: member_review member_review_reviewee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review
    ADD CONSTRAINT member_review_reviewee_id_fkey FOREIGN KEY (reviewee_id) REFERENCES public.member(id);


--
-- Name: member_review member_review_reviewer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review
    ADD CONSTRAINT member_review_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES public.member(id);


--
-- Name: member_review member_review_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review
    ADD CONSTRAINT member_review_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: member_review_tag member_review_tag_review_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.member_review_tag
    ADD CONSTRAINT member_review_tag_review_id_fkey FOREIGN KEY (review_id) REFERENCES public.member_review(id) ON DELETE CASCADE;


--
-- Name: notification notification_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.member(id);


--
-- Name: notification notification_recipient_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_recipient_id_fkey FOREIGN KEY (recipient_id) REFERENCES public.member(id);


--
-- Name: post post_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post
    ADD CONSTRAINT post_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: post_attachment post_attachment_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_attachment
    ADD CONSTRAINT post_attachment_file_id_fkey FOREIGN KEY (file_id) REFERENCES public.file_meta(id);


--
-- Name: post_attachment post_attachment_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_attachment
    ADD CONSTRAINT post_attachment_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.post(id);


--
-- Name: post post_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post
    ADD CONSTRAINT post_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.member(id);


--
-- Name: post_comment post_comment_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_comment
    ADD CONSTRAINT post_comment_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.member(id);


--
-- Name: post_comment post_comment_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_comment
    ADD CONSTRAINT post_comment_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.post(id);


--
-- Name: post_like post_like_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_like
    ADD CONSTRAINT post_like_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: post_like post_like_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_like
    ADD CONSTRAINT post_like_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.post(id);


--
-- Name: post post_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post
    ADD CONSTRAINT post_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.report(id);


--
-- Name: report report_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: report_evidence report_evidence_field_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_evidence
    ADD CONSTRAINT report_evidence_field_record_id_fkey FOREIGN KEY (field_record_id) REFERENCES public.field_record(id);


--
-- Name: report_evidence report_evidence_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_evidence
    ADD CONSTRAINT report_evidence_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.report(id);


--
-- Name: report_favorite report_favorite_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_favorite
    ADD CONSTRAINT report_favorite_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: report_favorite report_favorite_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_favorite
    ADD CONSTRAINT report_favorite_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.report(id);


--
-- Name: report report_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report
    ADD CONSTRAINT report_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: schedule schedule_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schedule
    ADD CONSTRAINT schedule_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: social_account social_account_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.social_account
    ADD CONSTRAINT social_account_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: stt_audio_cleanup_job stt_audio_cleanup_job_audio_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_audio_cleanup_job
    ADD CONSTRAINT stt_audio_cleanup_job_audio_file_id_fkey FOREIGN KEY (audio_file_id) REFERENCES public.file_meta(id);


--
-- Name: stt_dispatch_outbox stt_dispatch_outbox_audio_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_dispatch_outbox
    ADD CONSTRAINT stt_dispatch_outbox_audio_file_id_fkey FOREIGN KEY (audio_file_id) REFERENCES public.file_meta(id);


--
-- Name: stt_dispatch_outbox stt_dispatch_outbox_stt_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_dispatch_outbox
    ADD CONSTRAINT stt_dispatch_outbox_stt_job_id_fkey FOREIGN KEY (stt_job_id) REFERENCES public.stt_job(id);


--
-- Name: stt_job_attempt stt_job_attempt_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job_attempt
    ADD CONSTRAINT stt_job_attempt_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: stt_job_attempt stt_job_attempt_stt_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job_attempt
    ADD CONSTRAINT stt_job_attempt_stt_job_id_fkey FOREIGN KEY (stt_job_id) REFERENCES public.stt_job(id);


--
-- Name: stt_job stt_job_audio_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT stt_job_audio_file_id_fkey FOREIGN KEY (audio_file_id) REFERENCES public.file_meta(id);


--
-- Name: stt_job stt_job_checklist_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT stt_job_checklist_item_id_fkey FOREIGN KEY (checklist_item_id) REFERENCES public.checklist_item(id);


--
-- Name: stt_job stt_job_field_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT stt_job_field_record_id_fkey FOREIGN KEY (field_record_id) REFERENCES public.field_record(id);


--
-- Name: stt_job stt_job_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT stt_job_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: stt_job stt_job_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT stt_job_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.field_session(id);


--
-- Name: stt_job stt_job_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stt_job
    ADD CONSTRAINT stt_job_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: study study_apartment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study
    ADD CONSTRAINT study_apartment_id_fkey FOREIGN KEY (apartment_id) REFERENCES public.apartment(id);


--
-- Name: study_application study_application_applicant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_application
    ADD CONSTRAINT study_application_applicant_id_fkey FOREIGN KEY (applicant_id) REFERENCES public.member(id);


--
-- Name: study_application study_application_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_application
    ADD CONSTRAINT study_application_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: study study_leader_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study
    ADD CONSTRAINT study_leader_id_fkey FOREIGN KEY (leader_id) REFERENCES public.member(id);


--
-- Name: study_member study_member_member_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_member
    ADD CONSTRAINT study_member_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.member(id);


--
-- Name: study_member study_member_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_member
    ADD CONSTRAINT study_member_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- Name: study_notice study_notice_study_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.study_notice
    ADD CONSTRAINT study_notice_study_id_fkey FOREIGN KEY (study_id) REFERENCES public.study(id);


--
-- PostgreSQL database dump complete
--

\unrestrict 1Yyc9MjemAIlZBCx7rV8Ko21RgDhnu25ZT0URn9UtUt7vq1N1IBtY8fJPwiwSdJ

