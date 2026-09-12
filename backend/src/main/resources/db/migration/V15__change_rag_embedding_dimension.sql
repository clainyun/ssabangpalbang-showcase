-- AI-008: 임베딩 실행 환경을 SSAFY GPU에서 EC2 CPU로 옮긴다.
--   Qwen/Qwen3-Embedding-4B (2560차원, cuda) -> intfloat/multilingual-e5-base (768차원, cpu)
--
-- pgvector는 차원이 다른 vector 사이의 캐스팅을 지원하지 않는다.
-- 따라서 기존 색인을 먼저 비우고 컬럼 타입을 바꾼 뒤, 배치로 전량 재색인한다.
--   python -m app.rag.index_public --all
--   python -m app.rag.index_report --all
DELETE FROM apartment_rag_document;

ALTER TABLE apartment_rag_document
    ALTER COLUMN embedding TYPE vector(768);
