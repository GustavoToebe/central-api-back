# Papéis do banco: migrador e API — T16

Separa quem altera o schema de quem roda a API. Na Central, hoje as duas usam o mesmo usuário (dono de tudo). Com a separação, uma falha ou
injeção na API não consegue criar, alterar, apagar ou esvaziar tabelas, nem reescrever o histórico do Flyway. Sem configuração
nova nada muda: sem as variáveis `MIGRATION_DB_*` o Flyway continua usando `DB_URL`, `DB_USER` e `DB_PASSWORD`.

## Papéis
- **migrador**: dono de todo objeto do schema `public` (e `private`), o único que roda migrations. Sem superusuário, sem criar papel nem banco.
- **app**: usado pela API (`DB_USER`). Só SELECT, INSERT, UPDATE e DELETE nas tabelas e uso das sequências; nada no `flyway_schema_history`.
  Tem `BYPASSRLS`: as tabelas têm RLS ligado e **sem política**, e quem não é dono recebe zero linhas. A API continua sendo a única porta de entrada.

## Roteiro (banco já migrado, como a produção)
1. Backup do banco e janela de baixo uso. Não rode em produção sem ensaiar em staging.
2. Gere duas senhas fora do git. Substitua os marcadores de `scripts/roles-banco.sql` (`{{banco}}`, `{{migrador}}`, `{{app}}`, `{{senha_migrador}}`, `{{senha_app}}`) e execute com o administrador atual.
3. Execute `scripts/adotar-papeis-banco.sql` (marcador `{{migrador}}`): transfere a propriedade de tabelas, views, sequências avulsas, tipos e funções. Não toca em `auth`, `storage` nem extensões.
4. Configure o ambiente: `DB_USER`/`DB_PASSWORD` do papel **app**; `MIGRATION_DB_USER`/`MIGRATION_DB_PASSWORD` do papel **migrador** (`MIGRATION_DB_URL` só se a conexão for outra). Reinicie.
5. Confira: a API sobe e responde; uma migration nova passa a rodar só pelo migrador; `\dp` mostra o app sem DDL.

## O que o teste garante
`PapeisBancoIntegrationTest` roda esse roteiro num Postgres 17 descartável: banco migrado pelo administrador → papéis → adoção → migration nova (cria tabela, altera tabela antiga) **só com as credenciais do migrador** → o app lê, grava e apaga dados, inclusive na tabela nova (privilégios padrão) e sob RLS, e é recusado em CREATE, ALTER, DROP e TRUNCATE e no histórico do Flyway.

## Cuidados e limites
- **Supabase**: confirme em staging que a plataforma permite criar o papel com `BYPASSRLS` e que o `postgres` consegue transferir a propriedade. Se não permitir, a alternativa é uma política `USING (true)` para o papel app em cada tabela, o que exige decisão separada.
- Banco **novo** (vazio) continua sendo migrado pelo administrador até o baseline; as migrations antigas mexem em `auth` e `storage`. Adote os papéis depois.
- Migration futura que cria função com `SECURITY DEFINER`, extensão ou objeto fora de `public`/`private` precisa de revisão do grant.
- A senha do administrador deixa de ser usada pela API; guarde-a fora da VPS.
- Reverter: configurar a API de volta ao usuário administrador e, se quiser, transferir a propriedade de volta (`ALTER ... OWNER TO`). Nada é apagado.
