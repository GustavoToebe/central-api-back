-- Conta bancária completa: banco, agência, conta, titular, tipo, abertura/encerramento e chaves PIX.
-- Contas já cadastradas viram tipo OUTRA (sem exigir dados bancários); contas novas escolhem o tipo.
ALTER TABLE financeiro_conta
    ADD COLUMN tipo_conta varchar(20) NOT NULL DEFAULT 'OUTRA' CHECK (tipo_conta IN ('CORRENTE','POUPANCA','CAIXA','OUTRA')),
    ADD COLUMN banco varchar(120),
    ADD COLUMN agencia varchar(20),
    ADD COLUMN numero_conta varchar(30),
    ADD COLUMN titular varchar(120),
    ADD COLUMN data_abertura date,
    ADD COLUMN data_encerramento date,
    ADD COLUMN chaves_pix jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(chaves_pix) = 'array'),
    ADD CONSTRAINT ck_financeiro_conta_periodo CHECK (data_encerramento IS NULL OR data_abertura IS NULL OR data_encerramento >= data_abertura);
