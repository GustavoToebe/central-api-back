-- Papéis separados do banco (T16): quem altera o schema (migrador) não é quem roda a API (app).
-- Modelo, com os marcadores trocados antes de executar (exemplo no docs/papeis-banco.md):
--   {{banco}}  nome do banco               {{migrador}}  papel das migrations      {{app}}  papel da API
--   {{senha_migrador}} / {{senha_app}}  senhas geradas fora do git
-- Executar UMA vez por banco, com um usuário administrador, ANTES da primeira migration do migrador.
-- O migrador será o dono de todo objeto criado pelo Flyway; a API recebe só dados (SELECT/INSERT/UPDATE/DELETE e sequências).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '{{migrador}}') THEN
        CREATE ROLE {{migrador}} LOGIN PASSWORD '{{senha_migrador}}' NOSUPERUSER NOCREATEDB NOCREATEROLE;
    END IF;
    -- O app precisa ler e gravar tabelas com RLS ligado e sem política (a API é a única porta de entrada dos dados).
    -- Dono da tabela ignora RLS; como o app deixa de ser dono, precisa de BYPASSRLS. Sem ele, toda consulta voltaria vazia.
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '{{app}}') THEN
        CREATE ROLE {{app}} LOGIN PASSWORD '{{senha_app}}' NOSUPERUSER NOCREATEDB NOCREATEROLE BYPASSRLS;
    END IF;
END
$$;

GRANT CONNECT ON DATABASE {{banco}} TO {{migrador}}, {{app}};
-- Algumas migrations criam schema próprio (private); o migrador precisa poder.
GRANT CREATE ON DATABASE {{banco}} TO {{migrador}};
GRANT USAGE, CREATE ON SCHEMA public TO {{migrador}};
GRANT USAGE ON SCHEMA public TO {{app}};
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Tudo o que o migrador criar daqui para frente já nasce acessível ao app, só para dados.
ALTER DEFAULT PRIVILEGES FOR ROLE {{migrador}} IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO {{app}};
ALTER DEFAULT PRIVILEGES FOR ROLE {{migrador}} IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO {{app}};

-- Objetos que já existem (banco em uso): dão acesso de dados ao app. A propriedade vai para o migrador em adotar-papeis-banco.sql.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO {{app}};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO {{app}};


-- O histórico do Flyway é só do migrador: a API não precisa lê-lo nem pode reescrevê-lo.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL ON public.flyway_schema_history FROM {{app}};
    END IF;
END
$$;
