# Biblioteca Java comum

HmacAssinatura e Totp vêm de `br.com.servirea:servirea-comum:0.1.0`, pacote `br.com.servirea.comum.seguranca`. Fonte e testes: [servirea-comum](https://github.com/GustavoToebe/servirea-comum). Assinatura, comparação e MFA mantêm o comportamento. Serviços de domínio continuam neste backend; só seus imports mudam. Testes HTTP emulam o autenticador com CodigoTotpTeste, exclusivo de src/test, sem tornar pública a geração interna da biblioteca.

## Versões e validação local

Fixar versão no pom.xml; nunca usar LATEST, intervalo ou SNAPSHOT em produção. Antes de atualizar: revisar a versão, instalar/testar a biblioteca e executar toda a suíte do backend (`mvn -o -q verify`), conferindo target/surefire-reports. A versão 0.2.0, se publicada, não entra automaticamente nos backends.

Nesta rodada, `mvn -o install` na biblioteca disponibiliza 0.1.0 no cache local sem token. Para outra máquina consumir pelo registry, o usuário configura manualmente o servidor github no seu settings.xml; este trabalho não lê nem altera essa configuração.

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers><server>
    <id>github</id>
    <username>${env.GH_PACKAGE_USER}</username>
    <password>${env.GH_PACKAGE_TOKEN}</password>
  </server></servers>
</settings>
```

Exportar as duas variáveis somente na sessão de build. GitHub Packages Maven exige atualmente token pessoal **classic**, com `read:packages` e acesso ao repositório privado; token fine-grained não é aceito. A restrição por repositório solicitada não está disponível nesse tipo de token: usar uma identidade com acesso mínimo e prazo curto. [Documentação oficial](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry). Não colocar token no POM, Git, comando, log ou imagem.

## GitHub Actions

O job Maven tem packages: read e setup-java gera settings.xml em RUNNER_TEMP usando referência ao GITHUB_TOKEN do próprio Actions e GITHUB_ACTOR. A etapa de teste usa GITHUB_TOKEN do Actions por padrão; se existir o segredo GH_PACKAGES_READ_TOKEN, usa essa credencial de leitura. Não é necessário alterar o YAML amanhã. O workflow docs.yml só executa Python, portanto não precisa de credencial Maven.

Maven usa permissões herdadas do repositório produtor; **Manage Actions access não é o fluxo de permissões granulares desse registry**. Conferir o download entre repositórios privados com GITHUB_TOKEN. Se o token do consumidor não tiver acesso, configurar manualmente o segredo GH_PACKAGES_READ_TOKEN, com token classic de leitura, nos dois consumidores; o workflow já prioriza esse segredo quando definido. Não adicionar packages: write aos consumidores. A alternativa não foi configurada ou validada nesta sessão. [Permissões oficiais](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages).

## VPS e Docker

Dockerfiles usam BuildKit `--mount=type=secret,id=gh_token,required=false`. O settings temporário é removido no mesmo RUN, inclusive ao falhar. O token não usa ARG/ENV nem vai para a imagem. Sem segredo, só funciona se a dependência estiver disponível no cache; um build novo precisa de acesso ao pacote.

No deploy da Central, preparar manualmente `deploy/secrets/gh_token` (arquivo gitignorado, modo 600) com o token de leitura, pertencente ao usuário GitHub GustavoToebe. O Compose o fornece somente ao build das duas APIs. Esse arquivo não foi criado. Fora do Compose: `docker build --secret id=gh_token,src=/caminho/fora/do/git/gh_token .`.

## Antes de enviar os commits dos backends

Gerar o token correto e configurar sua máquina/VPS; conceder acesso de leitura aos consumidores; só então enviar melhoria/ecossistema-sem-ia e verificar a CI. Não fazer merge/deploy nesta rodada. A publicação da biblioteca foi feita pelo GITHUB_TOKEN próprio do workflow da tag, sem token pessoal.

Validação local desta rodada: 292 testes aprovados, zero falhas/erros/ignorados. Conferir os registros de [retomada](desenvolvimento/fontes-e-retomada.md).
