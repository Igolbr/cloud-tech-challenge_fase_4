# Monitoramento, alertas, segurança e governança

Texto do Bloco 2 (Caio) para o README e o vídeo. Tudo o que está aqui é código em
[template.yaml](../template.yaml) (aplicação) e [infra/conta.yaml](../infra/conta.yaml)
(conta AWS), exceto os passos marcados como **manual** no fim.

## Monitoramento

### Dashboard

O dashboard `plataforma-feedback-<ambiente>` no CloudWatch (o link sai no output `DashboardUrl`) mostra:

| Linha | Conteúdo |
|---|---|
| Alarmes | Estado atual dos 9 alarmes |
| Lambda | Invocações, erros, duração máxima (com a linha de 80% do timeout) e throttles das três funções |
| API | Requisições, erros 4xx e 5xx, latência p50 e p99 |
| Dados | Capacidade de leitura e escrita do DynamoDB, falhas registradas pela aplicação e idade do registro mais antigo no stream |
| Logs | As 50 falhas mais recentes nos logs das três funções (consulta do Logs Insights) |

### Logs

| Grupo de logs | Origem |
|---|---|
| `/plataforma-feedback/<ambiente>/receber-avaliacao` | Função receber-avaliacao |
| `/plataforma-feedback/<ambiente>/notificar-urgencia` | Função notificar-urgencia |
| `/plataforma-feedback/<ambiente>/gerar-relatorio-semanal` | Função gerar-relatorio-semanal |
| `/plataforma-feedback/<ambiente>/api-acesso` | Log de acesso da API (rota, status, latência) |

A retenção é de **30 dias** (parâmetro `RetencaoLogsDias`). Sem ela o CloudWatch guarda os logs
para sempre e o custo cresce sem limite.

### Alarmes

Todos avisam o **tópico de alarmes** (`plataforma-feedback-alarmes-<ambiente>`) quando disparam e
quando voltam ao normal. Esse tópico é separado do tópico de avaliações críticas, para que um problema
técnico não se misture com o feedback dos alunos.

| Alarme | Dispara quando | Por quê |
|---|---|---|
| `…-receber-avaliacao-erros` | 1 ou mais erros da função em 5 min | Avaliação não foi gravada |
| `…-notificar-urgencia-erros` | 1 ou mais erros da função em 5 min | Aviso crítico pode não ter saído |
| `…-gerar-relatorio-semanal-erros` | 1 ou mais erros da função em 5 min | Relatório da semana não foi enviado |
| `…-receber-avaliacao-duracao` | Duração ≥ 24 s (80% do timeout de 30 s) | Risco de timeout e 5xx |
| `…-notificar-urgencia-duracao` | Duração ≥ 24 s (80% de 30 s) | Risco de timeout |
| `…-gerar-relatorio-semanal-duracao` | Duração ≥ 48 s (80% de 60 s) | O volume cresceu; aumentar timeout ou memória |
| `…-api-5xx` | 1 ou mais respostas 5xx em 5 min | Aluno recebeu erro do servidor |
| `…-dynamodb-gravacao` | Falha ao gravar no DynamoDB | Soma das falhas vistas pela aplicação (inclusive acesso negado), dos erros de sistema e do throttling da tabela |
| `…-notificar-urgencia-falha-envio` | Falha ao publicar o aviso no SNS | A função trata esse erro e reprocessa o registro, então ele não aparece na métrica `Errors`; o alarme lê o log |

Os dois últimos usam **filtros de métrica** sobre os logs (`Falha ao gravar` e
`Falha ao processar registro`), publicados no namespace `PlataformaFeedback/<ambiente>`.
São 9 alarmes; a camada gratuita do CloudWatch cobre 10.

### Destinatários

- **Avisos de avaliação crítica e relatório semanal:** `EmailAdministrador` (segredo `EMAIL_ADMINISTRADOR`).
- **Alarmes e orçamento:** `EmailAlertas` (segredo `EMAIL_ALERTAS`); se vazio, vai para o `EmailAdministrador`.

Para cadastrar mais administradores no tópico de alarmes:

```bash
TOPICO=$(aws cloudformation describe-stacks --stack-name plataforma-feedback-dev \
  --query "Stacks[0].Outputs[?OutputKey=='TopicoAlarmesArn'].OutputValue" --output text)
aws sns subscribe --topic-arn "$TOPICO" --protocol email --notification-endpoint outro.admin@exemplo.com
```

Cada e-mail precisa clicar em **Confirm subscription** no e-mail "AWS Notification - Subscription Confirmation".

### Teste do alarme (erro forçado)

```bash
./scripts/forcar-erro.sh dev
```

O script invoca a `receber-avaliacao` com um corpo Base64 inválido. A função falha, sem gravar nada,
e em até 5 minutos o alarme `plataforma-feedback-dev-receber-avaliacao-erros` muda para `ALARM` e chega um
e-mail "ALARM: …" para os administradores. Depois de 5 minutos sem novos erros chega o e-mail "OK: …".

## Segurança

### Menor privilégio por função

Cada função tem um papel IAM próprio, definido no template, com só as ações que o código usa:

| Função | Pode | Não pode (exemplos) |
|---|---|---|
| receber-avaliacao | `dynamodb:PutItem` na tabela; escrever no próprio log | Ler, apagar ou alterar avaliações; publicar no SNS |
| notificar-urgencia | Ler o stream da tabela; `sns:Publish` no tópico de notificações; escrever no próprio log | Ler ou gravar a tabela; publicar no tópico de alarmes |
| gerar-relatorio-semanal | `dynamodb:Query` só no índice `por-dia`; `sns:Publish` no tópico de notificações; escrever no próprio log | Scan, gravar ou apagar na tabela; publicar no tópico de alarmes |

Para conferir (útil no vídeo):

```bash
./scripts/verificar-permissoes.sh dev
```

O script usa o simulador de políticas do IAM e mostra `allowed` ou `implicitDeny` para cada ação.

### Dados

- **Criptografia em repouso:** DynamoDB com SSE (chave KMS gerenciada pela AWS). Os logs do CloudWatch e os buckets S3 também são criptografados.
- **Backup:** Point-in-time recovery ligado na tabela (restauração em qualquer ponto dos últimos 35 dias).
- **Em trânsito:** a HTTP API do API Gateway só tem endpoint HTTPS. Os tópicos SNS e os buckets S3 negam qualquer acesso sem TLS (`aws:SecureTransport`).
- **Sem dado pessoal:** a avaliação guarda apenas `id`, `descricao`, `nota`, `urgencia`, `dataEnvio` e `dia`. O log de acesso da API foi configurado **sem IP e sem user agent**, então nenhum dado que identifique o aluno é registrado.

### API

- **Throttling:** 10 requisições/s em média com picos de até 20 (parâmetros `ApiLimiteRequisicoesPorSegundo` e `ApiLimiteRajada`). Acima disso a API responde `429 Too Many Requests` sem invocar a função.
- **Validação:** a `receber-avaliacao` rejeita corpo inválido com `400` antes de gravar.

## Governança

### Deploy sem chave de longa duração (OIDC)

O [infra/conta.yaml](../infra/conta.yaml) cria:

- O provedor OIDC do GitHub e o papel `plataforma-feedback-github-deploy`, que **só** o repositório configurado
  (no environment `dev` ou na branch `main`) consegue assumir, com um token temporário de até 1 hora.
- O papel `plataforma-feedback-cloudformation`, que o CloudFormation usa para criar os recursos. O papel do GitHub
  não cria recursos; ele só pode pedir ao CloudFormation para usar esse papel nas pilhas `plataforma-feedback-dev`/`-prod`.
  Esse papel só gerencia papéis IAM das pilhas da aplicação, então não consegue alterar os próprios papéis de deploy.
- O bucket de pacotes do SAM (criptografado, privado, apaga pacotes com mais de 30 dias).

Enquanto a variável `AWS_ROLE_ARN` não estiver cadastrada, o workflow continua usando o usuário de deploy
com chaves nos segredos (modo atual). Depois de cadastrada, as chaves podem ser apagadas do GitHub e
desativadas no IAM.

### Auditoria

Trilha `plataforma-feedback-auditoria` no CloudTrail: todas as regiões, eventos globais (IAM) e validação de
integridade dos arquivos de log. Os registros ficam 90 dias num bucket privado e criptografado.

### Acesso da equipe

Cada integrante usa um **usuário IAM nominal** no grupo `plataforma-feedback-equipe`, que dá:

- Leitura do console (`ReadOnlyAccess`) e CloudShell.
- Invocação das três funções e o simulador de políticas, para testes e para o vídeo.
- **MFA obrigatório:** sem MFA o usuário só consegue cadastrar o próprio MFA e trocar a senha; todo o resto é negado.

Ninguém usa a conta raiz no dia a dia.

### Custo

- **Tags** `Projeto=plataforma-feedback` e `Ambiente=<ambiente>` em funções, tabela, tópicos, logs, papéis e API.
- **Orçamento** `plataforma-feedback-<ambiente>`: US$ 10/mês por padrão (parâmetro `LimiteOrcamentoMensalUsd`
  ou variável `LIMITE_ORCAMENTO_USD` no GitHub; `0` desliga). Avisa no tópico de alarmes quando o gasto real
  passa de 80% e quando a previsão do mês passa de 100%.

## Passo a passo de configuração

### 1. Conta (uma vez, com um usuário administrador)

```bash
aws cloudformation deploy \
  --template-file infra/conta.yaml \
  --stack-name plataforma-feedback-conta \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides RepositorioGitHub=Igolbr/cloud-tech-challenge_fase_4

aws cloudformation describe-stacks --stack-name plataforma-feedback-conta \
  --query "Stacks[0].Outputs" --output table
```

Use `CriarProvedorOidc=nao` se a conta já tiver o provedor do GitHub, e `CriarTrilhaCloudTrail=nao` se já
existir uma trilha (a segunda trilha de eventos de gerenciamento é cobrada).

### 2. GitHub (Settings → Secrets and variables → Actions)

| Tipo | Nome | Valor |
|---|---|---|
| Variable | `AWS_ROLE_ARN` | Output `PapelDeployGitHubArn` |
| Variable | `AWS_CFN_EXEC_ROLE_ARN` | Output `PapelExecucaoCloudFormationArn` |
| Variable | `AWS_ARTIFACTS_BUCKET` | Output `BucketArtefatosNome` |
| Secret | `EMAIL_ADMINISTRADOR` | E-mail dos avisos e do relatório (já existe) |
| Secret (opcional) | `EMAIL_ALERTAS` | E-mail dos alarmes técnicos |
| Variable (opcional) | `LIMITE_ORCAMENTO_USD` | Limite do orçamento mensal (padrão 10) |
| Variable (opcional) | `AWS_REGION` | Região (padrão `us-east-1`) |

Depois do primeiro deploy por OIDC, apague os segredos `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY`.

### 3. Passos manuais (não dá para fazer por código)

- [ ] **MFA na conta raiz:** console → nome da conta → *Security credentials* → *Assign MFA device*.
- [ ] **Usuários nominais:** criar um usuário IAM por integrante (Igor, Caio, Luciano), com acesso ao console, no grupo `plataforma-feedback-equipe`; cada um cadastra o próprio MFA no primeiro login.
- [ ] **Confirmar as assinaturas** de e-mail do tópico de notificações e do tópico de alarmes.
- [ ] **Tags de custo:** Billing → *Cost allocation tags* → ativar `Projeto` e `Ambiente` (para filtrar o gasto por projeto no Cost Explorer).
- [ ] **Teste final:** `./scripts/forcar-erro.sh` gera o e-mail de alarme e `./scripts/verificar-permissoes.sh` mostra os acessos negados.

## Observações para o primeiro deploy deste bloco

- **A URL da API muda uma vez:** a API passou a ser declarada explicitamente (`ApiAvaliacoes`) para ter throttling e log de acesso. Pegue a nova URL no output `ApiUrl` e atualize o `requests.http`/Postman.
- **Logs antigos:** os grupos `/aws/lambda/<função>` criados antes deste bloco não são mais usados e não pertencem à pilha; podem ser apagados no console.
- **Conta de laboratório (AWS Academy):** criação de papéis IAM, OIDC, CloudTrail e orçamento costuma ser bloqueada. Nesse caso use `LimiteOrcamentoMensalUsd=0` e não implante o `infra/conta.yaml`.
