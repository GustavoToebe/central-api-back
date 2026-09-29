-- Cobrança cancelada não ocupa mais a competência: dá para emitir outra no mesmo período.
ALTER TABLE public.cobranca DROP CONSTRAINT cobranca_contratacao_id_competencia_inicio_key;

CREATE UNIQUE INDEX uq_cobranca_competencia_vigente
    ON public.cobranca (contratacao_id, competencia_inicio)
    WHERE status <> 'CANCELADA';
