-- Adoção dos papéis em banco que JÁ está migrado (T16): transfere para o migrador a propriedade dos objetos do schema public
-- (e private, se existir) para que as próximas migrations rodem sem o usuário administrador.
-- Marcador: {{migrador}}. Executar com o administrador que hoje é dono dos objetos, depois de roles-banco.sql, com a API parada
-- ou em janela de baixo uso (ALTER ... OWNER pede bloqueio curto). Faça backup antes. Não mexe em auth, storage nem extensões.
DO $$
DECLARE
    r record;
BEGIN
    -- Tabelas, views e sequências avulsas. Sequência ligada a coluna (serial/identity) acompanha a tabela e fica de fora.
    FOR r IN
        SELECT n.nspname AS esquema, c.relname AS nome,
               CASE c.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW'
                              WHEN 'f' THEN 'FOREIGN TABLE' ELSE 'TABLE' END AS tipo
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname IN ('public', 'private') AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i') AND d.classid = 'pg_class'::regclass)
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype = 'e')
    LOOP
        EXECUTE format('ALTER %s %I.%I OWNER TO {{migrador}}', r.tipo, r.esquema, r.nome);
    END LOOP;

    -- Tipos enumerados e domínios criados pelas migrations.
    FOR r IN
        SELECT n.nspname AS esquema, t.typname AS nome, CASE t.typtype WHEN 'd' THEN 'DOMAIN' ELSE 'TYPE' END AS tipo
        FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname IN ('public', 'private') AND t.typtype IN ('e', 'd')
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = t.oid AND d.deptype = 'e')
    LOOP
        EXECUTE format('ALTER %s %I.%I OWNER TO {{migrador}}', r.tipo, r.esquema, r.nome);
    END LOOP;

    -- Funções (gatilhos, RPCs). Pula as que pertencem a extensões.
    FOR r IN
        SELECT p.oid::regprocedure::text AS assinatura
        FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname IN ('public', 'private') AND p.prokind IN ('f', 'p')
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e')
    LOOP
        EXECUTE format('ALTER ROUTINE %s OWNER TO {{migrador}}', r.assinatura);
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'private') THEN
        EXECUTE 'ALTER SCHEMA private OWNER TO {{migrador}}';
    END IF;
END
$$;
