# Contrato versionado da API — T17

`docs/contrato-api.json` é gerado dos controllers e dos DTOs (records): lista cada rota com método, caminho, expressão de permissão, tipo do corpo e da resposta, mais o esquema dos tipos (campos, `?` para opcional, enums). Nunca é editado à mão.

## Como funciona
`ContratoApiTest` (integração) gera o contrato do código atual e compara com o arquivo versionado. Mudou rota, permissão ou campo de DTO sem atualizar o arquivo: a CI falha com a instrução. Para atualizar, de propósito e com a mudança revisada:
`mvn test -Dtest=ContratoApiTest -Dcontrato.atualizar=true` e commitar o arquivo. O segundo teste impede rota nova sem `@PreAuthorize`: só as da lista deliberada (login, perfil da própria sessão, inscrição e calendário públicos; na Central, login/refresh/logout e o webhook do Mercado Pago) podem ficar sem permissão.

## Conferência do front
`scripts/verificar-contrato-api.py` (nos repositórios de front) lê o contrato do back vizinho (ou `CONTRATO_API`) e confere que cada chamada montada com `environment.apiUrl` existe como rota. Segmentos dinâmicos como `/escalas/${id}/${acao}` casam com trechos literais. Roda local; sem o arquivo (CI só do front) ele avisa e passa.

## Limites
Não há clientes TypeScript gerados nem OpenAPI: gerar clientes exigiria escolher gerador e adotá-los nas telas, o que não foi feito. O contrato não descreve parâmetros de consulta, códigos de erro nem regras de validação além de obrigatório/opcional. Não há versionamento de rotas (`/v2`); compatibilidade com clientes antigos não é exigida (decisão do usuário).
