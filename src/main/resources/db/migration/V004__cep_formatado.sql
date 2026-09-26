-- CEP gravado com hífen (26/09/2026): a API passou a validar e padronizar
-- documentos e contatos (web/Formatos), e o CEP vai para o banco como
-- "85800-000", igual ao Servire. A V002 criou a coluna com 8 caracteres.

ALTER TABLE public.cliente ALTER COLUMN cep TYPE varchar(9);

UPDATE public.cliente
   SET cep = substr(cep, 1, 5) || '-' || substr(cep, 6)
 WHERE cep ~ '^[0-9]{8}$';
