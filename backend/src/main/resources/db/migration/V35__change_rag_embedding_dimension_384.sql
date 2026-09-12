-- AI-008: 임베딩 모델을 한국어 특화 경량 모델로 교체한다.
--   intfloat/multilingual-e5-base (768차원) -> dragonkue/multilingual-e5-small-ko-v2 (384차원)
--
-- 교체 근거: 검색이 아파트 단위로 후보를 먼저 좁히고 상위 60개를 전부 LLM에 넘기므로
-- 임베딩의 순위 정확도가 결과를 바꾸지 못한다. 같은 품질을 더 적은 자원으로 낸다.
--
-- pgvector는 차원이 다른 vector 사이의 캐스팅을 지원하지 않는다.
-- 따라서 기존 색인을 먼저 비우고 컬럼 타입을 바꾼 뒤, 배치로 전량 재색인한다.
--   python -m app.rag.index_public --all
--   python -m app.rag.index_report --all
DELETE FROM apartment_rag_document;

ALTER TABLE apartment_rag_document
    ALTER COLUMN embedding TYPE vector(384);
