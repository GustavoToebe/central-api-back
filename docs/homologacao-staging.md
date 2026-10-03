# Roteiro de homologação em staging

Nada do que foi entregue nas rodadas 1 a 21 foi homologado com provedores, aparelhos ou banco reais; só há testes automatizados. Este roteiro lista o que **uma pessoa** precisa fazer em staging (VPS e Supabase de teste, número e e-mail de teste) antes de ligar qualquer recurso para uma paróquia. Marque cada item com data e responsável; falha vira pendência no backlog, não "ok com ressalva".

## 0. Preparação
- [ ] Staging com os quatro componentes na `melhoria/ecossistema-sem-ia`, mesmo `docker-compose` da produção e bancos separados dos reais.
- [ ] Backup do banco de staging antes das migrations (Servirea V082, Central V016) e conferência de que subiram sem erro.
- [ ] Perfis de teste com os códigos novos: `VAGA_DISTRIBUIR`, `NOTIFICACAO*`, `PRIVACIDADE*`, `CHECKIN*`, e um perfil **sem** eles (deve ver 403 e não ver os botões).
- [ ] Paróquia de teste com pessoas fictícias, uma delas com o seu próprio número de WhatsApp e e-mail, `autorizaWhatsapp` ligado.

## 1. Notificações (F05/F13)
- [ ] Com os gatilhos desligados, finalizar escala e publicar aviso **não** enfileira nada (centro de entregas vazio).
- [ ] "Avisar escalados" por e-mail e por WhatsApp: mensagem chega, texto legível, só quem tem contato/autorização.
- [ ] Repetir o aviso na mesma versão responde 409; reabrir e finalizar de novo libera.
- [ ] Ligar o gatilho de finalizar e o de lembrete 24 h; conferir o horário de Brasília e que quem recusou não recebe.
- [ ] Cota mensal esgotada deixa os envios pendentes sem perdê-los.

## 2. Check-in e PWA (F15/F16)
- [ ] Abrir o check-in, abrir o link em um celular real, registrar presença; repetir (deve dizer "já registrado").
- [ ] Código expirado, de outra celebração e de pessoa não escalada: mesma mensagem genérica.
- [ ] Instalar o PWA no Android e no iPhone; sem internet aparece só a página "sem conexão"; depois de sair da conta nada de dados pessoais fica no aparelho.
- [ ] `sw.js` e `manifest.webmanifest` servidos sem cache longo pelo Caddy.

## 3. Distribuição por regras (F04)
- [ ] Prévia com dados reais de uma paróquia piloto: conferir se as sugestões fazem sentido para a coordenação e se os motivos de descarte são claros.
- [ ] Alterar indisponibilidade depois da prévia e tentar aplicar: deve recusar.

## 4. Privacidade (F09)
- [ ] Exportar os dados de uma pessoa: arquivo sem corpo de mensagem nem contato de terceiros; cuidados só com a permissão.
- [ ] Retenção: definir prazo, conferir a contagem, executar em dados de teste e verificar a anonimização. É irreversível.

## 5. Arquivos e fotos (T13)
- [ ] Trocar a foto de voluntário e de evento no Supabase real: a anterior sai do bucket.
- [ ] Derrubar a rede do Storage durante um envio e conferir a pendência em `arquivo_pendencia` e o job limpando depois.
- [ ] Inscrição pública com foto ponta a ponta (Turnstile real).

## 6. Painel de instâncias e métricas (F06/T14)
- [ ] "Atualizar as mais antigas" com duas paróquias de teste; derrubar uma e ver a falha contada sem virar zero.
- [ ] Prometheus em rede privada com a credencial de coleta: `promtool check rules`, coleta ok, acesso público negado, rotação da credencial.
- [ ] Alertmanager entregando por e-mail: troque os marcadores `COMPLETAR` de `deploy/monitoramento/alertmanager.yml`, crie os três segredos (`docker-compose.monitoramento.yml`), suba `prometheus` e `alertmanager`, e dispare `docker compose exec alertmanager amtool alert add alertname=TesteDeEntrega severity=warning job=teste`: o e-mail precisa chegar. A cadeia Prometheus → Alertmanager → receptor já passa em `deploy/testes/alertas.test.sh`; o envio real pelo Resend só se prova aqui.

## 7. Banco e release (T16/T01/T12)
- [ ] **Papéis do banco** em staging Supabase: criar papéis (`BYPASSRLS` permitido?), adotar, subir a API com `MIGRATION_DB_*`, rodar uma migration nova só pelo migrador. Se o Supabase não permitir `BYPASSRLS`, parar e decidir a alternativa.
- [ ] `deploy/atualizar.sh` real gera `releases/atual.json` e imagens com tag de commit.
- [ ] `deploy/rollback.sh --simular` e depois um rollback de verdade com o código antigo compatível; confirmar a recusa quando a migration avançou.
- [ ] Backup e restauração no **Supabase de staging** com `deploy/backup-banco.sh` e `restaurar-banco.sh` ([backup-restauracao](backup-restauracao.md)): meça tempo e tamanho, restaure em outro banco, reaplique os papéis (T16), suba a API contra ele. O ensaio local de 02/10/2026 passou; falta o provedor real, o volume real e o agendamento.

## 8. Login (T09)
- [ ] 10 erros de senha bloqueiam a conta e o 429 traz `Retry-After`; reiniciar a API não zera o contador (agora no banco).
- [ ] Ativar MFA, entrar com TOTP e com código de recuperação, desativar.

Ao terminar, registre o resultado em `RETOMADA` e só então decida, paróquia por paróquia, quais gatilhos ligar.
