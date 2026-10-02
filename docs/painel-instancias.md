# Painel de instâncias — F06/F26

Visão conjunta das paróquias provisionadas na Central, sem migration nova (usa `consumo_historico`, V015). Sem IA, sem envio de alertas e sem consulta em massa automática.

## Leitura
GET /instancias?nivel=&pagina=0 (ROLE_OPERADOR) lê, para cada contratação com provisionamento ATIVA e situação comercial diferente de CANCELADA (até 500), o **último resumo de consumo guardado** e classifica: CRITICO (algum recurso ATINGIDO ou EXCEDIDO), ATENCAO (ATENCAO ou INVENTARIO_PENDENTE), OK, ou SEM_DADOS (nunca consultada). Ordena do mais grave ao menos grave, depois por cliente; páginas de 30. A resposta traz contadores por nível e quantas estão defasadas (sem consulta nas últimas 48 h). Ausência de resumo nunca vira consumo zero e um resumo antigo é marcado como defasado, não como atual. Nada de pessoas, contatos ou documentos entra aqui.

## Atualização manual limitada
POST /instancias/atualizacao consulta, **em série e no máximo 5 por chamada**, as instâncias sem resumo ou com o mais antigo, usando o mesmo `ConsumoCentralService` da tela de consumo (HTTP assinado fora de transação, timeouts de 5 s e 10 s, resposta limitada). Uma atualização por vez (segunda chamada simultânea responde 400). Falha de uma instância é contada e não grava resumo novo. Devolve consultadas, falhas e quantas ainda aguardam. Não há agendador: o operador decide quando atualizar.

## Alertas externos
A Central **não envia** notificações. Ela exporta, no endpoint privado de métricas, `ecossistema_instancias_por_nivel{nivel}` e `ecossistema_instancias_defasadas` (cópia em memória por 5 minutos, agregados sem cliente, instância ou contratação nos rótulos). `deploy/monitoramento/alertas.yml` ganhou `InstanciaComLimiteAtingido` (15 min) e `InstanciasSemResumoAtual` (1 h). Entregar o alerta a alguém depende do Alertmanager/canal escolhido pela operação, ainda não configurado; alerta preparado não é alerta entregue.

## Limites
Sem histórico de tendência no painel (há 90 dias por instância na tela de consumo), sem agendamento, sem agrupar por diocese e sem versões/healthcheck das instâncias (a versão implantada continua exigindo o manifesto de release). Acima de 500 instâncias o painel precisa de paginação no banco.
