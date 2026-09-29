-- Isenção (29/09/2026). A cobrança do Servirea Free, de R$ 0,00, nascia em aberto e virava "Vencida".
-- 1. Nova situação de cobrança ISENTA: não conta como recebido, não vence e ocupa a competência
--    (o índice único parcial da V009 só libera a CANCELADA).
-- 2. A contratação pode ficar isenta de cobrança, com motivo e, se quiser, até uma competência.
-- 3. As cobranças em aberto de valor zero que já existem passam a isentas.

ALTER TABLE public.cobranca DROP CONSTRAINT cobranca_status_check;
ALTER TABLE public.cobranca ADD CONSTRAINT cobranca_status_check
    CHECK (status IN ('ABERTA', 'PAGA', 'CANCELADA', 'ISENTA'));

ALTER TABLE public.contratacao ADD COLUMN isenta boolean NOT NULL DEFAULT false;
ALTER TABLE public.contratacao ADD COLUMN isencao_motivo text;
ALTER TABLE public.contratacao ADD COLUMN isenta_ate date;
ALTER TABLE public.contratacao ADD CONSTRAINT contratacao_isencao_check
    CHECK (NOT isenta OR isencao_motivo IS NOT NULL);

COMMENT ON COLUMN public.contratacao.isenta_ate IS
    'Último dia da última competência isenta. Vazio com isenta = sem data para acabar.';

UPDATE public.cobranca
SET status = 'ISENTA', observacao = COALESCE(observacao, 'Valor zero')
WHERE status = 'ABERTA' AND valor = 0;
