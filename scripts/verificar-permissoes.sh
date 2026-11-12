#!/usr/bin/env bash
# Mostra, com o simulador de políticas do IAM, que cada função só consegue fazer o que precisa.
# "allowed" = permitido; "implicitDeny" = negado (não está na política do papel).
#
# Uso: ./scripts/verificar-permissoes.sh [ambiente]   (padrão: dev)
# Requer permissão iam:SimulatePrincipalPolicy (o grupo da equipe em infra/conta.yaml já tem).
set -euo pipefail

AMBIENTE="${1:-dev}"
PILHA="plataforma-feedback-${AMBIENTE}"

saida() {
  aws cloudformation describe-stacks --stack-name "$PILHA" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

CONTA="$(aws sts get-caller-identity --query Account --output text)"
REGIAO="$(aws configure get region || echo "${AWS_REGION:-us-east-1}")"
TABELA="arn:aws:dynamodb:${REGIAO}:${CONTA}:table/$(saida TabelaAvaliacoesNome)"
INDICE="${TABELA}/index/por-dia"
TOPICO="$(saida TopicoNotificacoesArn)"
TOPICO_ALARMES="$(saida TopicoAlarmesArn)"

simular() {
  local papel="$1" recurso="$2"; shift 2
  aws iam simulate-principal-policy \
    --policy-source-arn "$papel" \
    --action-names "$@" \
    --resource-arns "$recurso" \
    --query "EvaluationResults[].[EvalActionName,EvalDecision]" \
    --output text | sed "s|^|    |; s|\$|    ($(basename "$recurso"))|"
}

echo "== receber-avaliacao (esperado: só PutItem) =="
PAPEL="$(saida PapelReceberAvaliacaoArn)"
simular "$PAPEL" "$TABELA" dynamodb:PutItem dynamodb:GetItem dynamodb:Scan dynamodb:DeleteItem dynamodb:UpdateItem
simular "$PAPEL" "$TOPICO" sns:Publish

echo "== notificar-urgencia (esperado: só publicar no tópico de notificações) =="
PAPEL="$(saida PapelNotificarUrgenciaArn)"
simular "$PAPEL" "$TABELA" dynamodb:PutItem dynamodb:Scan dynamodb:GetItem
simular "$PAPEL" "$TOPICO" sns:Publish
simular "$PAPEL" "$TOPICO_ALARMES" sns:Publish

echo "== gerar-relatorio-semanal (esperado: só Query no índice e publicar no tópico de notificações) =="
PAPEL="$(saida PapelGerarRelatorioSemanalArn)"
simular "$PAPEL" "$INDICE" dynamodb:Query
simular "$PAPEL" "$TABELA" dynamodb:Scan dynamodb:PutItem dynamodb:DeleteItem
simular "$PAPEL" "$TOPICO" sns:Publish
simular "$PAPEL" "$TOPICO_ALARMES" sns:Publish
