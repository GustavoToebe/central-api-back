-- Reserva durável do outbox; a rede roda fora da transação.
ALTER TABLE evento_saida ADD COLUMN reservado_por uuid;
ALTER TABLE evento_saida ADD COLUMN reserva_ate timestamptz;
ALTER TABLE evento_saida ADD CONSTRAINT ck_evento_saida_reserva
    CHECK ((reservado_por IS NULL) = (reserva_ate IS NULL));
CREATE INDEX ix_evento_saida_reserva_contratacao ON evento_saida (contratacao_id, reserva_ate)
    WHERE reserva_ate IS NOT NULL;
