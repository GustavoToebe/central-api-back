-- Erros de servidor dos aplicativos (contrato 6.2, decisão de 26/09/2026),
-- para a tela "Logs" do painel. Só dado técnico: o usuário vem pelo id do
-- app, nunca nome ou e-mail (a Central não guarda dado pessoal dos apps).
-- O id é o do app: reenviar o mesmo lote não duplica. Apagados depois de 90 dias.

CREATE TABLE public.erro_aplicativo (
    id              uuid PRIMARY KEY,
    produto_id      uuid NOT NULL REFERENCES public.produto (id),
    contratacao_id  uuid REFERENCES public.contratacao (id),
    tenant_id       uuid,
    usuario_id      uuid,
    ocorrido_em     timestamptz NOT NULL,
    recebido_em     timestamptz NOT NULL DEFAULT now(),
    metodo          varchar(10),
    rota            varchar(300),
    status          integer NOT NULL,
    codigo          varchar(60),
    mensagem        varchar(1000),
    request_id      varchar(100)
);

CREATE INDEX idx_erro_aplicativo_ocorrido ON public.erro_aplicativo (ocorrido_em DESC);
CREATE INDEX idx_erro_aplicativo_contratacao ON public.erro_aplicativo (contratacao_id, ocorrido_em DESC);

ALTER TABLE public.erro_aplicativo ENABLE ROW LEVEL SECURITY;
