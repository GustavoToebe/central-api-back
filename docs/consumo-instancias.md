# Consumo de instâncias na Central

Abra uma contratação provisionada e clique **Ver consumo da instância**. A tela /contratacoes/{id}/consumo consulta uma instância sob demanda; Atualizar consumo refaz a consulta sem cache. Não varre todas as paróquias nem agrega pessoas de várias instâncias em uma carga automática.

GET Central /contratacoes/{id}/consumo exige ROLE_OPERADOR. Destino vem da contratação/produto no servidor, nunca de URL/tenant enviados pelo navegador. Escalares são carregados em transação curta; HTTP assinado ocorre fora dela. Timeouts do cliente: conexão 5 s/leitura 10 s; resposta até 1 MiB. Falha de rede, status diferente de 200, versão/identidade inválidas ou payload incompleto geram 502 CONSUMO_INDISPONIVEL, sem retornar zero ou último resultado como se fosse atual. Troca de contratação cancela resposta anterior na interface.

Servirea GET /integracao/v1/instancias/{id}/consumo passa pelo HMAC/nonce, exige PERM_INTEGRACAO e retorna versaoContrato=1, tenantId, contratacaoId, consumo e funcionalidades. Contexto de tenant é definido antes de abrir sessão REQUIRES_NEW/read-only/repeatable-read e restaurado em finally. Só este caminho interno aceita tenant explícito. Contagens de domínio usam JPQL, sem SQL nativo. Nunca retorna nomes, fichas, contatos ou caminhos de arquivos.

Consumo inclui planoNome, versaoDireitos, direitosConfirmadosEm, consultadoEm e itens {codigo,nome,usado,limite,disponivel,estado,unidade,pendentes,competencia}. Funcionalidades é lista explícita. Valida identidade da contratação e instância antes de devolver os agregados. Não modifica cobranças, planos ou cotas.

Recursos: pessoas, voluntarios, usuarios, armazenamento_mb, emails_mes, whatsapp_mes, importacoes_mes. Armazenamento está em bytes no contrato e é convertido para MB (1.048.576) na tela. Inclui somente arquivos vinculados/anexos retidos, não ocupação física do bucket. Fotos com tamanho desconhecido têm pendentes e disponível=null; o painel mostra inventário pendente. E-mail/WhatsApp contam primeira reserva de mensagem na fila, não cada tentativa ou entrega; importações contam lotes CSV confirmados. Competência mensal de Brasília. Sem limite configurado não equivale a consumo zero.

O endpoint legado GET /integracao/v1/instancias/{id} mantém uso.armazenamento_mb=0 como placeholder histórico; não é usado pelo painel novo e não serve para medição/faturamento. Integrações novas devem usar o endpoint de consumo. Histórico centralizado, alertas programados, varredura de várias instâncias, filas de consulta e métricas de infraestrutura permanecem pendentes em F06.

## Histórico de 02/10/2026

[Histórico de consumo](historico-consumo.md): diário sob demanda, 90 dias, ausência não significa zero.
