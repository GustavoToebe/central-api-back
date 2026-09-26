-- Teste de telas de 26/09/2026.
-- 1. Trimestral e semestral além de mensal e anual (preço do plano e contratação).
-- 2. A cobrança passa a ter itens: o plano e cada adicional contratado. Antes o
--    valor copiava só o preço do plano e o adicional nunca era cobrado. O item é
--    uma cópia do momento em que a cobrança foi gerada ou recalculada: mudar o
--    catálogo depois não reescreve cobrança antiga.
-- 3. Valor padrão do recurso do tipo LIMITE, sugerido ao montar o plano.

ALTER TABLE public.preco_plano DROP CONSTRAINT preco_plano_periodicidade_check;
ALTER TABLE public.preco_plano ADD CONSTRAINT preco_plano_periodicidade_check
    CHECK (periodicidade IN ('MENSAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL'));

ALTER TABLE public.contratacao DROP CONSTRAINT contratacao_periodicidade_check;
ALTER TABLE public.contratacao ADD CONSTRAINT contratacao_periodicidade_check
    CHECK (periodicidade IN ('MENSAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL'));

ALTER TABLE public.recurso ADD COLUMN valor_padrao numeric(12, 2) CHECK (valor_padrao IS NULL OR valor_padrao >= 0);

CREATE TABLE public.cobranca_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cobranca_id     uuid NOT NULL REFERENCES public.cobranca (id) ON DELETE CASCADE,
    ordem           integer NOT NULL,
    tipo            varchar(20) NOT NULL CHECK (tipo IN ('PLANO', 'ADICIONAL')),
    descricao       varchar(300) NOT NULL,
    quantidade      numeric(12, 2) NOT NULL CHECK (quantidade > 0),
    valor_unitario  numeric(10, 2) NOT NULL CHECK (valor_unitario >= 0),
    meses           integer NOT NULL CHECK (meses > 0),
    valor           numeric(10, 2) NOT NULL CHECK (valor >= 0)
);

-- Sem UNIQUE (cobranca_id, ordem): ao refazer os itens, o Hibernate insere os
-- novos antes de apagar os antigos (orphanRemoval) no mesmo flush.
CREATE INDEX idx_cobranca_item_cobranca ON public.cobranca_item (cobranca_id, ordem);

COMMENT ON COLUMN public.cobranca_item.valor_unitario IS
    'PLANO: preço do período. ADICIONAL: preço mensal do pacote; valor = quantidade × unitário × meses.';

-- Cobranças já existentes ganham um item com o valor do plano.
INSERT INTO public.cobranca_item (cobranca_id, ordem, tipo, descricao, quantidade, valor_unitario, meses, valor)
SELECT c.id, 0, 'PLANO', 'Plano ' || p.nome, 1, c.valor, 1, c.valor
  FROM public.cobranca c
  JOIN public.contratacao ct ON ct.id = c.contratacao_id
  JOIN public.plano p ON p.id = ct.plano_id;

ALTER TABLE public.cobranca_item ENABLE ROW LEVEL SECURITY;
