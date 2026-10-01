# Limites de login

POST /auth/login conta tentativas por IP de conexão e por e-mail normalizado antes de chamar autenticação/BCrypt. Padrão: 60/IP e 10/conta por janela fixa de 600 segundos. Tentativas bem-sucedidas também contam; e-mails existentes/inexistentes recebem os mesmos limites. Não usar X-Forwarded-For diretamente; produção depende do proxy confiável já configurado.

Excesso retorna 429, código LOGIN_LIMITADO e Retry-After em segundos. A interface usa a mensagem normal da API. Não há bloqueio permanente nem reset de contador pelo login bem-sucedido. O limite protege autenticação; DTO inválido continua 400 e outras rotas de recuperação/refresh precisam de revisão própria.

Estado em memória desta instância: teto de 20000 chaves, identificadores com hash e sal aleatório, sem e-mail/IP em logs. Limpeza de expirados no máximo uma vez por minuto durante requisições. Saturação recusa nova chave até limpeza, sem expulsar contador ativo. Reinício limpa estado. **Não é proteção distribuída**: antes de múltiplas réplicas, coordenar contador no banco/serviço compartilhado e rever limites para redes compartilhadas.

Configuração: central.auth.limite-ip, limite-conta, janela-segundos e max-chaves. Variáveis correspondentes em .env.example. Valores positivos obrigatórios; janela até 86400 segundos. Default ativo em todos os profiles; testes específicos usam limites reduzidos e relógio controlado.

Testes: HTTP 429/Retry-After, serviço não chamado após limite, conta normalizada entre IPs, concorrência, expiração e saturação. MFA opt-in de operador acrescentado em V013: [contrato e operação](mfa-operadores.md). As ações de configuração compartilham estes contadores; login com segundo fator também conta cada requisição.
