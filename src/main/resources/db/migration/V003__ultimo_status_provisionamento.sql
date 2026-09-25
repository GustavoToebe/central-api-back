-- Última resposta do aplicativo ao POST de provisionamento (26/09/2026).
-- NULL = nunca enviado; 0 = sem resposta (rede, timeout); senão o status
-- HTTP. Serve para decidir se o operador ainda pode editar nome, slug e
-- administrador: só antes do primeiro envio ou depois de uma recusa 4xx que
-- não seja 409. Depois de 2xx, 5xx, falha de rede ou 409 a paróquia pode já
-- existir no app, e mudar o corpo faria a mesma Idempotency-Key voltar 409
-- para sempre.

ALTER TABLE public.contratacao ADD COLUMN ultimo_status_provisionamento integer;

COMMENT ON COLUMN public.contratacao.ultimo_status_provisionamento IS
    'Status HTTP da última resposta ao provisionamento; 0 = sem resposta; NULL = nunca enviado.';
COMMENT ON COLUMN public.contratacao.idempotency_key IS
    'Sempre igual ao id: o app exige Idempotency-Key = contratacaoId do corpo.';
