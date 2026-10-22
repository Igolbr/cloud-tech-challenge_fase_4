package br.com.fiap.feedback.receber;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Função receber-avaliacao.
 *
 * <p>Responsabilidade única: validar e gravar a avaliação recebida em {@code POST /avaliacao}.
 * Responde 201 com a avaliação criada, 400 em caso de dados inválidos ou 500 em falha inesperada.
 *
 * <p>Variáveis de ambiente: {@code TABELA_AVALIACOES} e {@code FUSO_HORARIO}.
 */
public class ReceberAvaliacaoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ValidadorAvaliacao validador;
    private final AvaliacaoRepository repository;

    /** Construtor usado pelo runtime da Lambda: clientes criados uma vez e reaproveitados entre invocações. */
    public ReceberAvaliacaoHandler() {
        this(
                new ValidadorAvaliacao(MAPPER, ZoneId.of(env("FUSO_HORARIO", "America/Sao_Paulo"))),
                new DynamoDbAvaliacaoRepository(
                        DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
                        env("TABELA_AVALIACOES", null)));
    }

    public ReceberAvaliacaoHandler(ValidadorAvaliacao validador, AvaliacaoRepository repository) {
        this.validador = validador;
        this.repository = repository;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context context) {
        Avaliacao avaliacao;
        try {
            avaliacao = validador.validar(corpo(evento));
        } catch (ValidacaoException e) {
            log(context, "Avaliação rejeitada: " + e.getMessage());
            return resposta(400, Map.of("erros", e.getErros()));
        }

        try {
            repository.salvar(avaliacao);
        } catch (RuntimeException e) {
            // O texto "Falha ao gravar" alimenta a métrica FalhasGravacaoDynamoDB (template.yaml)
            log(context, "Falha ao gravar avaliação " + avaliacao.id() + ": " + e);
            // Relança para a Lambda registrar o erro (métrica Errors) e a API responder 500
            throw e;
        }

        log(context, "Avaliação " + avaliacao.id() + " gravada com urgência " + avaliacao.urgencia());
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", avaliacao.id());
        corpo.put("descricao", avaliacao.descricao());
        corpo.put("nota", avaliacao.nota());
        corpo.put("urgencia", avaliacao.urgencia().name());
        corpo.put("dataEnvio", avaliacao.dataEnvio().toString());
        return resposta(201, corpo);
    }

    private static String corpo(APIGatewayV2HTTPEvent evento) {
        if (evento == null || evento.getBody() == null) {
            return null;
        }
        if (evento.getIsBase64Encoded()) {
            return new String(Base64.getDecoder().decode(evento.getBody()), StandardCharsets.UTF_8);
        }
        return evento.getBody();
    }

    private static APIGatewayV2HTTPResponse resposta(int status, Object corpo) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json; charset=utf-8"))
                    .withBody(MAPPER.writeValueAsString(corpo))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar resposta", e);
        }
    }

    private static void log(Context context, String mensagem) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(mensagem);
        }
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            if (padrao == null) {
                throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + nome);
            }
            return padrao;
        }
        return valor;
    }
}
