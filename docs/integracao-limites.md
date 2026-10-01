# Limites de integração v1

Atualização de 01/10/2026 na branch melhoria/ecossistema-sem-ia.

- O receptor verifica chave, janela de 300 segundos e formato de assinatura/nonce antes de ler o corpo.
- O corpo de entrada tem limite de 1.048.576 bytes (1 MiB), inclusive sem Content-Length.
- Tamanho excedido retorna HTTP 413, código `CORPO_EXCEDIDO`, no formato `ApiError`; nenhum nonce é consumido.
- Assinatura permanece `v1=` e 64 caracteres hexadecimais minúsculos. O contrato solicita UUID novo por tentativa; o filtro admite nonce não vazio de até 128 caracteres por compatibilidade.
- Assinatura válida continua obrigatória antes da gravação atômica do nonce. Repetição retorna `NONCE_REPETIDO`.
- Os logs de recusa registram apenas o código, sem headers, corpo ou credenciais fornecidos pelo cliente.
- Estes limites protegem `/integracao/**`. O Caddy do ecossistema limita adicionalmente as entradas públicas de API a 6 MiB, preservando o multipart atual.

Os emissores existentes usam UUID para nonce e payloads pequenos. Antes de integrar exportações volumosas, paginar as requisições e manter o limite nos dois lados.

Testes: `mvn test -Dtest=IntegracaoFiltroTest,HmacAssinaturaTest`; os testes HTTP com banco continuam obrigatórios antes de publicar.

## Testes no Windows dentro do Codex

Se o JDK falhar em `UnixDomainSockets.connect` ao criar o cliente HTTP ("Unable to establish loopback connection"), criar um diretório de sockets sob Documents/Codex e passar `-DargLine=-Djdk.net.unixdomain.tmpdir=<diretorio>` ao Maven. Isto é um ajuste da execução local; não incluir caminho Windows no profile de produção. A propriedade e o problema de virtualização de arquivos são descritos pela [equipe OpenJDK](https://mail.openjdk.org/pipermail/nio-dev/2023-March/013297.html).

Em 01/10/2026, `mvn -o -B -q verify` com essa propriedade passou em PostgreSQL 17 via Testcontainers. Nenhum segredo ou configuração de produção foi necessário.
