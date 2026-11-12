#!/usr/bin/env bash
# Faz login no Cognito e imprime o token de acesso (JWT) do aluno, válido por 1 hora.
#
# Use a saída no cabeçalho "Authorization: Bearer <token>" do POST /avaliacao:
#   TOKEN=$(./scripts/obter-token.sh aluno.teste dev)
#
# Uso: ./scripts/obter-token.sh <usuario> [ambiente]   (padrão: dev)
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 <usuario> [ambiente]" >&2
  exit 1
fi

command -v jq >/dev/null || { echo "Instale o jq para usar este script." >&2; exit 1; }

USUARIO="$1"
AMBIENTE="${2:-dev}"
PILHA="plataforma-feedback-${AMBIENTE}"

saida_da_pilha() {
  aws cloudformation describe-stacks --stack-name "$PILHA" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

credenciais_em_json() {
  jq -n --arg usuario "$USUARIO" --arg senha "$1" '{USERNAME: $usuario, PASSWORD: $senha}'
}

read -rsp "Senha de ${USUARIO}: " SENHA
echo >&2

aws cognito-idp initiate-auth \
  --client-id "$(saida_da_pilha ClienteApiAvaliacoesId)" \
  --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters "$(credenciais_em_json "$SENHA")" \
  --query "AuthenticationResult.AccessToken" \
  --output text
