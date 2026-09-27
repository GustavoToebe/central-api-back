-- V008: fecha o schema public para a Data API do Supabase (27/09/2026).
--
-- As tabelas da Central já nascem com RLS e sem policy (V001-V007), mas no
-- Supabase o public dá grant padrão para anon/authenticated (inclusive na
-- flyway_schema_history). Quem usa o banco é só a API, como dona das
-- tabelas. Em banco sem os papéis do Supabase (dev, testes) a parte de grants
-- não faz nada.

DO $$
DECLARE
    t record;
    papel text;
BEGIN
    FOR t IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity
          -- O Flyway segura esta tabela em outra conexão durante a migração (ALTER esperaria para
          -- sempre); ela fica fechada pela falta de grant abaixo.
          AND c.relname <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.relname);
    END LOOP;

    FOREACH papel IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = papel) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', papel);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', papel);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', papel);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', papel);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', papel);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', papel);
        END IF;
    END LOOP;
END
$$;
