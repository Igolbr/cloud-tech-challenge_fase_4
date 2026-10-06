package br.com.fiap.feedback.notificar;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Função notificar-urgencia.
 *
 * <p>Responsabilidade única: avisar os administradores sobre avaliações críticas.
 * É acionada pelo DynamoDB Streams a cada nova avaliação gravada; o template já filtra
 * apenas inserções com urgência CRITICA, e a função confere novamente por segurança.
 *
 * <p>Usa "partial batch response": se o envio de um registro falhar, só ele (e os seguintes)
 * são reprocessados, evitando e-mails duplicados para os que já foram enviados.
 *
 * <p>Variáveis de ambiente: {@code TOPICO_ALERTA_ARN} e {@code FUSO_HORARIO}.
 */
public class NotificarUrgenciaHandler implements RequestHandler<DynamodbEvent, StreamsEventResponse> {

    private final Notificador notificador;
    private final ZoneId fusoHorario;

    public NotificarUrgenciaHandler() {
        this(
                new SnsNotificador(
                        SnsClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
                        env("TOPICO_ALERTA_ARN", null)),
                ZoneId.of(env("FUSO_HORARIO", "America/Sao_Paulo")));
    }

    public NotificarUrgenciaHandler(Notificador notificador, ZoneId fusoHorario) {
        this.notificador = notificador;
        this.fusoHorario = fusoHorario;
    }

    @Override
    public StreamsEventResponse handleRequest(DynamodbEvent evento, Context context) {
        List<StreamsEventResponse.BatchItemFailure> falhas = new ArrayList<>();
        if (evento == null || evento.getRecords() == null) {
            return new StreamsEventResponse(falhas);
        }

        for (DynamodbEvent.DynamodbStreamRecord registro : evento.getRecords()) {
            try {
                if (!"INSERT".equals(registro.getEventName())) {
                    continue;
                }
                Optional<AlertaUrgencia> alerta = AlertaUrgencia.deItem(registro.getDynamodb().getNewImage());
                if (alerta.isEmpty()) {
                    continue;
                }
                notificador.enviar(alerta.get().assunto(), alerta.get().mensagem(fusoHorario));
                log(context, "Alerta enviado para a avaliação " + alerta.get().id());
            } catch (RuntimeException e) {
                log(context, "Falha ao processar registro " + registro.getEventID() + ": " + e);
                // Em streams, a Lambda retoma a partir do primeiro registro com falha
                falhas.add(new StreamsEventResponse.BatchItemFailure(registro.getDynamodb().getSequenceNumber()));
                break;
            }
        }
        return new StreamsEventResponse(falhas);
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
