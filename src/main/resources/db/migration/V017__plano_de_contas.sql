-- Plano de contas do financeiro da Central: a categoria plana vira grupo (organiza) ou conta contábil (recebe lançamentos).
-- Sem grupo_id = grupo; com grupo_id = conta contábil, sempre do mesmo tipo do grupo. Só a conta contábil recebe lançamento.
ALTER TABLE financeiro_categoria ADD COLUMN tipo varchar(20), ADD COLUMN grupo_id uuid;

-- Categorias existentes viram contas contábeis: saída se algum lançamento for despesa, entrada se só houver receita, saída quando nunca usada.
UPDATE financeiro_categoria c
   SET tipo = COALESCE((SELECT CASE WHEN count(*) = 0 THEN NULL WHEN bool_or(m.tipo = 'DESPESA') THEN 'DESPESA' ELSE 'RECEITA' END
                          FROM financeiro_movimento m WHERE m.categoria_id = c.id), 'DESPESA');

-- Um grupo "migradas" por tipo que já tinha categoria; as categorias antigas entram nele.
WITH base AS (SELECT DISTINCT tipo FROM financeiro_categoria),
novos AS (
  INSERT INTO financeiro_categoria (nome, ativo, tipo)
  SELECT n.nome || CASE WHEN EXISTS (SELECT 1 FROM financeiro_categoria x WHERE lower(x.nome) = lower(n.nome)) THEN ' – plano de contas' ELSE '' END, true, b.tipo
    FROM base b CROSS JOIN LATERAL (SELECT CASE b.tipo WHEN 'DESPESA' THEN 'Saídas (migradas)' ELSE 'Entradas (migradas)' END AS nome) n
  RETURNING id, tipo)
UPDATE financeiro_categoria c SET grupo_id = n.id FROM novos n WHERE c.tipo = n.tipo AND c.id <> n.id;

ALTER TABLE financeiro_categoria ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE financeiro_categoria ADD CONSTRAINT ck_financeiro_categoria_tipo CHECK (tipo IN ('RECEITA', 'DESPESA'));
ALTER TABLE financeiro_categoria ADD CONSTRAINT ck_financeiro_categoria_grupo CHECK (grupo_id IS NULL OR grupo_id <> id);
ALTER TABLE financeiro_categoria ADD CONSTRAINT fk_financeiro_categoria_grupo FOREIGN KEY (grupo_id) REFERENCES financeiro_categoria (id);
CREATE INDEX ix_financeiro_categoria_grupo ON financeiro_categoria (grupo_id);

-- O nome era único no sistema inteiro; agora é único entre os grupos de um tipo e entre as contas de um mesmo grupo.
DROP INDEX ux_financeiro_categoria_nome;
CREATE UNIQUE INDEX ux_financeiro_grupo_nome ON financeiro_categoria (tipo, lower(nome)) WHERE grupo_id IS NULL;
CREATE UNIQUE INDEX ux_financeiro_conta_contabil_nome ON financeiro_categoria (grupo_id, lower(nome)) WHERE grupo_id IS NOT NULL;
