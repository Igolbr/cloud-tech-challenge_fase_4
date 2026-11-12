#!/usr/bin/env bash
# Cadastra um aluno no User Pool do Cognito para ele poder chamar a API.
#
# O autocadastro está desligado no template, então só a equipe cria alunos.
# A senha é pedida no terminal (não aparece na tela) e já fica definitiva.
#
# Uso: ./scripts/cadastrar-aluno.sh <usuario> [ambiente]   (padrão: dev)
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 <usuario> [ambiente]" >&2
  exit 1
fi

USUARIO="$1"
AMBIENTE="${2:-dev}"
PILHA="plataforma-feedback-${AMBIENTE}"

saida_da_pilha() {
  aws cloudformation describe-stacks --stack-name "$PILHA" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

ler_senha() {
  local senha
  read -rsp "Senha de ${USUARIO} (mínimo 12 caracteres, com maiúscula, minúscula e número): " senha
  echo >&2
  printf '%s' "$senha"
}

CADASTRO_ID="$(saida_da_pilha CadastroAlunosId)"
SENHA="$(ler_senha)"

aws cognito-idp admin-create-user \
  --user-pool-id "$CADASTRO_ID" \
  --username "$USUARIO" \
  --message-action SUPPRESS >/dev/null

aws cognito-idp admin-set-user-password \
  --user-pool-id "$CADASTRO_ID" \
  --username "$USUARIO" \
  --password "$SENHA" \
  --permanent

echo "Aluno ${USUARIO} cadastrado no User Pool ${CADASTRO_ID}."
