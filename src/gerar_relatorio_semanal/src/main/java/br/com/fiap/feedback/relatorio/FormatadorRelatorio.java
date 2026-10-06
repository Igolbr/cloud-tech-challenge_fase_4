package br.com.fiap.feedback.relatorio;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Gera o texto do e-mail do relatório semanal. */
public class FormatadorRelatorio {

    /**
     * Limite de avaliações listadas individualmente. O SNS aceita mensagens de até 256 KB;
     * acima do limite a lista é truncada, mas média e contagens continuam considerando todas.
     */
    public static final int LIMITE_AVALIACOES_LISTADAS = 100;

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId fusoHorario;

    public FormatadorRelatorio(ZoneId fusoHorario) {
        this.fusoHorario = fusoHorario;
    }

    public String assunto(RelatorioSemanal relatorio) {
        return "Relatório semanal de avaliações - " + DIA.format(relatorio.inicio()) + " a " + DIA.format(relatorio.fim());
    }

    public String mensagem(RelatorioSemanal relatorio) {
        StringBuilder texto = new StringBuilder();
        texto.append("RELATÓRIO SEMANAL DE AVALIAÇÕES\n")
                .append("Período: ").append(DIA.format(relatorio.inicio()))
                .append(" a ").append(DIA.format(relatorio.fim())).append("\n\n");

        texto.append("RESUMO\n")
                .append("Total de avaliações: ").append(relatorio.total()).append('\n')
                .append("Média das notas: ")
                .append(relatorio.media().map(m -> m.toPlainString().replace('.', ',')).orElse("sem avaliações no período"))
                .append("\n\n");

        texto.append("QUANTIDADE DE AVALIAÇÕES POR DIA\n");
        relatorio.quantidadePorDia().forEach((dia, quantidade) ->
                texto.append("  ").append(DIA.format(dia)).append(": ").append(quantidade).append('\n'));
        texto.append('\n');

        texto.append("QUANTIDADE DE AVALIAÇÕES POR URGÊNCIA\n");
        relatorio.quantidadePorUrgencia().forEach((urgencia, quantidade) ->
                texto.append("  ").append(urgencia).append(": ").append(quantidade).append('\n'));
        texto.append('\n');

        texto.append("AVALIAÇÕES (descrição | urgência | data de envio)\n");
        List<AvaliacaoRegistrada> avaliacoes = relatorio.avaliacoes();
        if (avaliacoes.isEmpty()) {
            texto.append("  Nenhuma avaliação recebida no período.\n");
        }
        avaliacoes.stream().limit(LIMITE_AVALIACOES_LISTADAS).forEach(avaliacao -> texto
                .append("  - ").append(avaliacao.descricao())
                .append(" | ").append(avaliacao.urgencia())
                .append(" | ").append(DATA_HORA.format(avaliacao.dataEnvio().atZone(fusoHorario)))
                .append('\n'));
        if (avaliacoes.size() > LIMITE_AVALIACOES_LISTADAS) {
            texto.append("  ... e mais ").append(avaliacoes.size() - LIMITE_AVALIACOES_LISTADAS)
                    .append(" avaliações não listadas (incluídas na média e nas contagens).\n");
        }

        texto.append("\nHorários no fuso ").append(fusoHorario).append('.');
        return texto.toString();
    }
}
