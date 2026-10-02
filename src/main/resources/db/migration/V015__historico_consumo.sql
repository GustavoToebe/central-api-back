CREATE TABLE consumo_historico(id uuid PRIMARY KEY,contratacao_id uuid NOT NULL REFERENCES contratacao(id),dia date NOT NULL,consultado_em timestamptz NOT NULL,dados text NOT NULL CHECK(length(dados)<=32768),UNIQUE(contratacao_id,dia));
CREATE INDEX consumo_historico_dia_idx ON consumo_historico(dia);
ALTER TABLE consumo_historico ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON consumo_historico FROM anon;
 END IF;
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON consumo_historico FROM authenticated;
 END IF;
END $$;
