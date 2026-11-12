#!/usr/bin/env bash
# Provoca um erro real na função receber-avaliacao para testar o alarme e o e-mail.
#
# Envia direto para a Lambda um evento com corpo marcado como Base64, mas inválido.
# A decodificação falha, a função termina com erro (métrica Errors = 1) e, em até
# ~5 minutos, o alarme "plataforma-feedback-<ambiente>-receber-avaliacao-erros" dispara
# e manda e-mail para o tópico de alarmes. Nada é gravado na tabela.
#
# Uso: ./scripts/forcar-erro.sh [ambiente]   (padrão: dev)
set -euo pipefail

AMBIENTE="${1:-dev}"
FUNCAO="receber-avaliacao-${AMBIENTE}"
SAIDA="$(mktemp)"

echo "Invocando ${FUNCAO} com um evento inválido..."
aws lambda invoke \
  --function-name "$FUNCAO" \
  --cli-binary-format raw-in-base64-out \
  --payload '{"isBase64Encoded": true, "body": "%%% erro forçado para testar o alarme %%%"}' \
  "$SAIDA" >/dev/null

echo "Resposta da função:"
cat "$SAIDA"; echo
rm -f "$SAIDA"

echo
echo "Pronto. Acompanhe o alarme (leva até 5 minutos para mudar para ALARM):"
echo "  aws cloudwatch describe-alarms --alarm-names plataforma-feedback-${AMBIENTE}-receber-avaliacao-erros \\"
echo "    --query 'MetricAlarms[0].[AlarmName,StateValue,StateReason]' --output text"
