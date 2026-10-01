-- Despesas e receitas adicionais da operação SaaS; cobranças comerciais não são copiadas.
CREATE TABLE financeiro_conta (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), nome varchar(120) NOT NULL,
 saldo_inicial numeric(14,2) NOT NULL DEFAULT 0, data_saldo_inicial date NOT NULL,
 ativo boolean NOT NULL DEFAULT true);
CREATE UNIQUE INDEX ux_financeiro_conta_nome ON financeiro_conta(lower(nome));
CREATE TABLE financeiro_categoria (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), nome varchar(120) NOT NULL, ativo boolean NOT NULL DEFAULT true);
CREATE UNIQUE INDEX ux_financeiro_categoria_nome ON financeiro_categoria(lower(nome));
CREATE TABLE financeiro_movimento (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), versao bigint NOT NULL DEFAULT 0,
 descricao varchar(200) NOT NULL, tipo varchar(20) NOT NULL CHECK(tipo IN ('RECEITA','DESPESA')),
 situacao varchar(20) NOT NULL DEFAULT 'PENDENTE' CHECK(situacao IN ('PENDENTE','PAGO','CANCELADO')),
 valor numeric(14,2) NOT NULL CHECK(valor>0), vencimento date NOT NULL, data_pagamento date,
 conta_id uuid NOT NULL REFERENCES financeiro_conta(id), categoria_id uuid NOT NULL REFERENCES financeiro_categoria(id),
 observacoes varchar(1000), criado_em timestamptz NOT NULL DEFAULT now(),
 CHECK ((situacao='PAGO') = (data_pagamento IS NOT NULL)));
CREATE INDEX ix_financeiro_movimento_vencimento ON financeiro_movimento(vencimento,id);
CREATE INDEX ix_financeiro_movimento_baixa ON financeiro_movimento(data_pagamento,conta_id) WHERE situacao='PAGO';
ALTER TABLE financeiro_conta ENABLE ROW LEVEL SECURITY;
ALTER TABLE financeiro_categoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE financeiro_movimento ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON financeiro_conta,financeiro_categoria,financeiro_movimento FROM anon;
 END IF;
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON financeiro_conta,financeiro_categoria,financeiro_movimento FROM authenticated;
 END IF;
END $$;
