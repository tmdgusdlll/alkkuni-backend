-- 제품명 부분 일치·유사도 검색용 (ADR-003)
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- 로케일이 C면 한글 trigram이 만들어지지 않아 한글 검색이 조용히 0건이 된다. 배포 시점에 막는다.
DO $$
BEGIN
    IF cardinality(public.show_trgm('가')) = 0 THEN
        RAISE EXCEPTION 'pg_trgm이 한글을 인식하지 못함 (LC_CTYPE=%)',
            (SELECT datctype FROM pg_database WHERE datname = current_database());
    END IF;
END $$;
