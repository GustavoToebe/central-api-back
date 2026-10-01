# Entrega do outbox com reserva durável

`EntregaIntegracao` usa três etapas: reservar em transação curta, enviar HTTP fora da transação, concluir em outra transação curta. A conexão do banco não fica ocupada esperando o aplicativo.

## Concorrência e recuperação

- Até 30 candidatos por ciclo, ordenados por próxima tentativa e id. Contratação travada antes do evento; alterações comerciais usam a mesma ordem.
- `reservado_por` identifica a tentativa, `reserva_ate` vence após 120 segundos. Timeout de conexão de 5 segundos e leitura de 10 segundos.
- Só uma reserva ativa por contratação, inclusive quando outra versão de direitos substituiu o evento anterior. Um segundo worker ignora a reserva ativa.
- Se a aplicação morrer, a reserva vence e outro ciclo retoma. Nenhum processo precisa limpar flags na memória.
- Conclusão só grava se ainda possuir a reserva ativa; resposta atrasada não libera a reserva de outra tentativa.
- POST pode criar a instância enquanto os direitos mudam. A conclusão preserva a versão comercial atual e grava a identidade criada; o próximo evento envia PUT com tenantId correto. Identidade não fica editável depois do início de um POST de resultado desconhecido.

## Garantia de entrega

Entrega continua **pelo menos uma vez**, com idempotência do POST no aplicativo pela chave da contratação e versão dos direitos no PUT. Reserva durável reduz duplicações concorrentes, mas não promete exatamente uma chamada HTTP em caso de crash ou expiração.

Agenda de repetição preservada. 409/422 são definitivos; resposta 2xx de provisionamento sem UUID válido vira erro sem deixar a reserva presa. Logs de resposta usam código HTTP; não armazenam corpo do aplicativo.

Migration V011 é aditiva, com check de presença pareada e índice para reserva por contratação. Gerar schema com o script parametrizado em banco descartável. Testes cobrem HTTP sem transação, segundo worker, crash/expiração, posse substituída e direitos alterados durante o POST.
