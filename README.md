# Plataforma de Feedback de Aulas – FIAP Tech Challenge Fase 4

Plataforma serverless em AWS onde estudantes avaliam aulas e administradores recebem
avisos de avaliações críticas e um relatório semanal com a média das notas.

Toda a infraestrutura é código ([template.yaml](template.yaml), AWS SAM) e o deploy é
feito automaticamente pelo GitHub Actions a cada push na `main`.

## Arquitetura

```
                 POST /avaliacao
 Aluno ───────► API Gateway (HTTP) ───► receber-avaliacao ───► DynamoDB "avaliacoes"
                                         (valida e grava)          │   (Streams)
                                                                   ▼
 Administrador ◄── e-mail ◄── SNS ◄──────────────────────── notificar-urgencia
       ▲                       ▲                             (só urgência CRITICA)
       │                       │
       └───── e-mail ──────────┴──── gerar-relatorio-semanal ◄── EventBridge Scheduler
                                      (lê os últimos 7 dias)      (segunda, 08:00)

 CloudWatch Logs: todas as funções
```

**Modelo de nuvem:** nuvem pública (AWS), com FaaS (AWS Lambda) para o código, serviços
gerenciados para dados (DynamoDB) e mensageria (SNS, EventBridge Scheduler), e
infraestrutura como código (AWS SAM/CloudFormation).

| Componente | Serviço AWS | Papel |
|---|---|---|
| API | API Gateway HTTP API | Expõe `POST /avaliacao` em HTTPS |
| Funções | AWS Lambda (Java 21, arm64) | Uma responsabilidade por função |
| Banco | DynamoDB on-demand, criptografado, com Streams | Guarda as avaliações e dispara o aviso de urgência |
| Notificação | SNS com assinatura de e-mail | Entrega o aviso de urgência e o relatório |
| Agendamento | EventBridge Scheduler | Dispara o relatório toda semana |
| Logs | CloudWatch Logs | Registro de execução das funções e acesso à API (retenção de 30 dias) |
| Monitoramento | CloudWatch Dashboard e Alarms | Painel das funções/API e 9 alarmes enviados por e-mail (tópico SNS separado) |
| Governança | IAM, CloudTrail, AWS Budgets | Papel próprio por função, auditoria e alerta de orçamento |

### Regra de urgência

| Nota | Urgência | Envia e-mail imediato? |
|---|---|---|
| 0 a 3 | `CRITICA` | Sim |
| 4 a 6 | `MEDIA` | Não |
| 7 a 10 | `BAIXA` | Não |

A urgência é calculada em `receber-avaliacao` e gravada no item.

## Funções

### receber-avaliacao

- **Responsabilidade:** validar e gravar a avaliação.
- **Gatilho:** `POST /avaliacao` (API Gateway HTTP API).
- **Entrada:** `{"descricao": string, "nota": int de 0 a 10}`.
  `descricao` não pode ser vazia (máx. 2000 caracteres); `nota` deve ser inteiro (`"7"` e `7.5` são rejeitados).
- **Saída:** `201` com a avaliação criada ou `400` com a lista de erros.
- **Variáveis de ambiente:** `TABELA_AVALIACOES`, `FUSO_HORARIO`.
- **Permissões:** apenas `dynamodb:PutItem` na tabela.
- **Código:** [src/receber_avaliacao](src/receber_avaliacao)

```bash
curl -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d '{"descricao": "O áudio da aula 3 está inaudível", "nota": 2}'
```

```json
{
  "id": "6f1c2a52-...",
  "descricao": "O áudio da aula 3 está inaudível",
  "nota": 2,
  "urgencia": "CRITICA",
  "dataEnvio": "2026-10-06T15:00:00Z"
}
```

Item gravado no DynamoDB: `id`, `descricao`, `nota`, `urgencia`, `dataEnvio` (ISO-8601, UTC)
e `dia` (`yyyy-MM-dd` no fuso da aplicação, chave do índice `por-dia`). Nenhum dado pessoal
do aluno é armazenado.

### notificar-urgencia

- **Responsabilidade:** avisar os administradores sobre avaliações críticas.
- **Gatilho:** DynamoDB Streams. O filtro do evento só invoca a função para inserções com `urgencia = CRITICA`.
- **Saída:** e-mail via SNS com descrição, urgência e data de envio.
- **Falhas:** usa *partial batch response*; só o registro que falhou é reprocessado (até 3 tentativas).
- **Variáveis de ambiente:** `TOPICO_ALERTA_ARN`, `FUSO_HORARIO`.
- **Permissões:** leitura do stream e publicação no tópico SNS.
- **Código:** [src/notificar_urgencia](src/notificar_urgencia)

### gerar-relatorio-semanal

- **Responsabilidade:** consolidar a semana e enviar o relatório.
- **Gatilho:** EventBridge Scheduler, toda segunda-feira às 08:00 (America/Sao_Paulo).
- **Período:** os 7 dias completos anteriores à execução (segunda a domingo).
- **Saída:** e-mail via SNS com média das notas, quantidade de avaliações por dia, quantidade
  por urgência e a lista de avaliações (descrição, urgência e data de envio).
- **Variáveis de ambiente:** `TABELA_AVALIACOES`, `TOPICO_RELATORIO_ARN`, `FUSO_HORARIO`.
- **Permissões:** apenas `dynamodb:Query` no índice `por-dia` e publicação no tópico SNS.
- **Código:** [src/gerar_relatorio_semanal](src/gerar_relatorio_semanal)

Para gerar o relatório na hora (ex.: na demonstração):

```bash
aws lambda invoke --function-name gerar-relatorio-semanal-dev /dev/stdout
```

## Estrutura do repositório

```
├── template.yaml                 # Infraestrutura (AWS SAM)
├── samconfig.toml                # Configuração do sam build/deploy
├── pom.xml                       # Agregador Maven (mvn verify roda os testes das 3 funções)
├── src/
│   ├── receber_avaliacao/        # Função 1 (Maven, com testes em src/test)
│   ├── notificar_urgencia/       # Função 2
│   └── gerar_relatorio_semanal/  # Função 3
├── events/                       # Eventos de exemplo para sam local invoke
├── requests.http                 # Requisições de teste da API
├── infra/conta.yaml              # Governança da conta: OIDC do GitHub, CloudTrail, grupo da equipe
├── scripts/                      # forcar-erro.sh (teste de alarme) e verificar-permissoes.sh (IAM)
├── docs/                         # Monitoramento e segurança
└── .github/workflows/deploy.yml  # CI/CD
```

## Desenvolvimento local

Pré-requisitos: Java 21, Maven 3.9+, [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html) e Docker (para `sam local`).

```bash
# Testes unitários das três funções
mvn verify

# Build das funções
sam build

# Invocar uma função com evento de exemplo (as funções acessam a AWS real;
# copie events/env.example.json, ajuste os nomes/ARNs e passe com --env-vars)
sam local invoke ReceberAvaliacaoFunction -e events/avaliacao-critica.json --env-vars env.local.json

# Subir a API localmente em http://127.0.0.1:3000/avaliacao (use requests.http ou o Postman)
sam local start-api --env-vars env.local.json
```

## Deploy

### Automático (GitHub Actions)

O workflow [.github/workflows/deploy.yml](.github/workflows/deploy.yml):

1. Em todo push e pull request: roda `mvn verify` (testes) e `cfn-lint` no template.
2. Em push na `main`: roda `sam build` e `sam deploy`, e imprime a URL da API.

Configuração única no GitHub (**Settings → Secrets and variables → Actions**):

| Tipo | Nome | Valor |
|---|---|---|
| Secret | `AWS_ACCESS_KEY_ID` | Chave do usuário de deploy |
| Secret | `AWS_SECRET_ACCESS_KEY` | Segredo do usuário de deploy |
| Secret | `EMAIL_ADMINISTRADOR` | E-mail que recebe os avisos e o relatório |
| Secret (opcional) | `EMAIL_ALERTAS` | E-mail dos alarmes técnicos (padrão: o mesmo do administrador) |
| Variable (opcional) | `AWS_REGION` | Região (padrão `us-east-1`) |
| Variable (opcional) | `LIMITE_ORCAMENTO_USD` | Limite do alerta de orçamento mensal (padrão 10; 0 desliga) |
| Variables (OIDC) | `AWS_ROLE_ARN`, `AWS_CFN_EXEC_ROLE_ARN`, `AWS_ARTIFACTS_BUCKET` | Outputs do `infra/conta.yaml`; com elas o deploy dispensa as chaves de acesso |

### Manual (primeira vez ou sem pipeline)

```bash
sam build
sam deploy --parameter-overrides EmailAdministrador=voce@exemplo.com
```

A URL da API aparece na saída `ApiUrl`.

### Confirmar a assinatura do e-mail

Depois do primeiro deploy, a AWS envia ao `EmailAdministrador` um e-mail
"AWS Notification - Subscription Confirmation". Clique em **Confirm subscription**;
sem isso nenhum aviso ou relatório é entregue.

## Monitoramento e segurança

Dashboard, alarmes, destinatários, papéis IAM, criptografia, throttling, OIDC, CloudTrail, MFA e
orçamento estão descritos em [docs/monitoramento-e-seguranca.md](docs/monitoramento-e-seguranca.md).

```bash
./scripts/forcar-erro.sh dev           # provoca um erro e o alarme manda e-mail em até 5 min
./scripts/verificar-permissoes.sh dev  # mostra o que cada função pode e não pode fazer
```

## Licença

[MIT](LICENSE)
