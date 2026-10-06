package br.com.fiap.feedback.relatorio;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Função gerar-relatorio-semanal.
 *
 * <p>Responsabilidade única: consolidar as avaliações da semana e enviar o relatório por e-mail.
 * Acionada pelo EventBridge Scheduler uma vez por semana. O período são os 7 dias completos
 * anteriores ao dia da execução (ex.: executando na segunda, cobre de segunda a domingo anteriores).
 *
 * <p>Variáveis de ambiente: {@code TABELA_AVALIACOES}, {@code TOPICO_RELATORIO_ARN} e {@code FUSO_HORARIO}.
 */
public class GerarRelatorioSemanalHandler implements RequestHandler<Map<String, Object>, String> {

    static final int DIAS_DO_RELATORIO = 7;

    private final AvaliacaoConsulta consulta;
    private final Notificador notificador;
    private final Clock clock;
    private final ZoneId fusoHorario;

    public GerarRelatorioSemanalHandler() {
        this(
                new DynamoDbAvaliacaoConsulta(
                        DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
                        env("TABELA_AVALIACOES", null)),
                new SnsNotificador(
                        SnsClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
                        env("TOPICO_RELATORIO_ARN", null)),
                Clock.systemUTC(),
                ZoneId.of(env("FUSO_HORARIO", "America/Sao_Paulo")));
    }

    public GerarRelatorioSemanalHandler(AvaliacaoConsulta consulta, Notificador notificador, Clock clock, ZoneId fusoHorario) {
        this.consulta = consulta;
        this.notificador = notificador;
        this.clock = clock;
        this.fusoHorario = fusoHorario;
    }

    @Override
    public String handleRequest(Map<String, Object> evento, Context context) {
        LocalDate hoje = LocalDate.now(clock.withZone(fusoHorario));
        LocalDate fim = hoje.minusDays(1);
        LocalDate inicio = hoje.minusDays(DIAS_DO_RELATORIO);

        List<AvaliacaoRegistrada> avaliacoes = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            avaliacoes.addAll(consulta.buscarPorDia(dia));
        }

        RelatorioSemanal relatorio = RelatorioSemanal.calcular(inicio, fim, avaliacoes);
        FormatadorRelatorio formatador = new FormatadorRelatorio(fusoHorario);
        notificador.enviar(formatador.assunto(relatorio), formatador.mensagem(relatorio));

        String resumo = "Relatório de " + inicio + " a " + fim + " enviado: " + relatorio.total()
                + " avaliações, média " + relatorio.media().map(Object::toString).orElse("-");
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(resumo);
        }
        return resumo;
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
