# Release por commit e rollback — T01

Scripts em `deploy/` (Central). Nada muda no pipeline do GitHub: o `atualizar.sh` continua sendo chamado do mesmo jeito.

## O que `atualizar.sh` passou a fazer
1. Volta cada repositório para a `main` antes do `pull` (necessário depois de um rollback, que deixa o HEAD solto).
2. Dá às imagens das APIs uma **tag imutável** igual ao commit curto (`servirea-api:<12 caracteres>`, `central-api:<12 caracteres>`), via `docker-compose.yml`. O mesmo commit sempre gera a mesma tag, e imagens antigas ficam disponíveis para voltar sem reconstruir.
3. Depois que os serviços sobem saudáveis, registra o **manifesto** da release em `releases/<data>.json` e atualiza `releases/atual.json` e `releases/anterior.json` (a anterior só muda quando algo mudou de verdade). Falha ao registrar o manifesto não desfaz o deploy.

## Manifesto (`deploy/manifesto.sh`)
JSON com, para cada um dos quatro componentes, o commit completo, a última migration (APIs) e o id da imagem local (APIs). Uma linha por componente, para ser lido sem `jq`.

## Rollback (`deploy/rollback.sh [--simular] [--aceitar-migrations]`)
Volta o **código** ao que está em `releases/anterior.json`: cada repositório vai ao commit anterior, as APIs sobem a imagem já construída (`--no-build`; se não existir mais, reconstrói do commit), os fronts são recompilados e a subida espera a saúde. Só registra o novo estado se subiu saudável e guarda o manifesto revertido em `releases/revertida-*.json`.
- **Recusa** voltar se a release atual aplicou migration que a anterior não conhece (código 3), porque o Flyway só avança. Só avance com `--aceitar-migrations` depois de conferir que a migration é compatível ou de restaurar o backup.
- `--simular` mostra cada passo sem alterar nada.
- O rollback é temporário: o próximo deploy da `main` traz tudo de volta para a frente. Para ficar na versão antiga, reverta o commit na `main`.

## Teste
`deploy/testes/release.test.sh` monta quatro repositórios git de mentira, com `docker` e `flock` simulados, e confere: manifesto, ordem natural das migrations, simulação sem efeito, rollback com e sem imagem local, recusa por migration nova, `--aceitar-migrations` e a falta de release anterior. Roda na CI (job `testar`) e local: `bash deploy/testes/release.test.sh`.

## Limites
Não há verificação de negócio depois do deploy além do healthcheck do compose; backup e restauração do banco têm scripts e foram ensaiados só localmente ([backup-restauracao](backup-restauracao.md), T12); falta o ensaio no Supabase real e o agendamento. Imagens antigas não são removidas automaticamente (`docker image prune -f` só apaga as sem tag): faça a limpeza manual, mantendo ao menos as duas últimas. O pipeline ainda não passa o commit pedido ao script, então o deploy publica a `main` atual dos quatro repositórios, como antes. Backup do `releases/` faz parte do backup da VPS. Esta entrega não executou deploy nem rollback em produção.
