CREATE TABLE login_tentativa(
 chave varchar(100) PRIMARY KEY,
 janela_ate timestamptz NOT NULL,
 tentativas integer NOT NULL CHECK(tentativas>=1));
CREATE INDEX login_tentativa_janela_idx ON login_tentativa(janela_ate);
ALTER TABLE login_tentativa ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON login_tentativa FROM anon; END IF;
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON login_tentativa FROM authenticated; END IF;
END $$;
