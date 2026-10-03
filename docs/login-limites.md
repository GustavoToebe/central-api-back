# Limites de login

POST /auth/login conta tentativas por IP de conexão e por e-mail normalizado antes de chamar autenticação/BCrypt. Padrão: 60/IP e 10/conta por janela fixa de 600 segundos. Tentativas bem-sucedidas também contam; e-mails existentes/inexistentes recebem os mesmos limites. Não usar X-Forwarded-For diretamente; produção depende do proxy confiável já configurado.

Excesso retorna 429, código LOGIN_LIMITADO e Retry-After em segundos. A interface usa a mensagem normal da API. Não há bloqueio permanente nem reset de contador pelo login bem-sucedido. O limite protege autenticação; DTO inválido continua 400 e outras rotas de recuperação/refresh precisam de revisão própria.

Estado no banco (V082 no Servirea, V016 na Central, tabela global `login_tentativa`): uma instrução atômica conta e lê, então vale entre réplicas, sobrevive a reinício e tentativas concorrentes não passam do limite. A chave é um hash (SHA-256 com sal derivado do segredo JWT); e-mail e IP nunca são gravados em claro nem logados. Linhas vencidas há mais de uma hora são removidas, no máximo uma vez por minuto por processo. **Compromisso aceito:** como a conta é limitada pelo e-mail, quem erra a senha de outra pessoa 10 vezes a bloqueia por 10 minutos; o limite por IP (60) é maior para não punir várias pessoas atrás do mesmo NAT. Trocar isso por contagem só de falhas ou por captcha progressivo é decisão futura.

Configuração: central.auth.limite-ip, limite-conta e janela-segundos. Variáveis correspondentes em .env.example. Valores positivos obrigatórios; janela até 86400 segundos. Default ativo em todos os profiles; testes específicos usam limites reduzidos e relógio controlado.

Testes: HTTP 429/Retry-After, serviço não chamado após limite, conta normalizada entre IPs, concorrência (inclusive no banco, com duas instâncias), expiração e reinício. MFA opt-in de operador acrescentado em V013: [contrato e operação](mfa-operadores.md). As ações de configuração compartilham estes contadores; login com segundo fator também conta cada requisição.
