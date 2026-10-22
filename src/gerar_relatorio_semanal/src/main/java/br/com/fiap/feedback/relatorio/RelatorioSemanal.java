package br.com.fiap.feedback.relatorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consolidação das avaliações de um período: média das notas, quantidade por dia
 * e quantidade por urgência, além da lista de avaliações (descrição, urgência e data).
 */
public record RelatorioSemanal(
        LocalDate inicio,
        LocalDate fim,
        List<AvaliacaoRegistrada> avaliacoes,
        Optional<BigDecimal> media,
        Map<LocalDate, Long> quantidadePorDia,
        Map<String, Long> quantidadePorUrgencia) {

    /** Ordem de exibição das urgências (da mais grave para a menos grave). */
    public static final List<String> URGENCIAS = List.of("CRITICA", "MEDIA", "BAIXA");

    /**
     * Calcula o relatório do período [inicio, fim], ambos inclusivos.
     * Dias e urgências sem avaliações aparecem com quantidade zero.
     */
    public static RelatorioSemanal calcular(LocalDate inicio, LocalDate fim, List<AvaliacaoRegistrada> avaliacoes) {
        List<AvaliacaoRegistrada> ordenadas = avaliacoes.stream()
                .sorted(Comparator.comparing(AvaliacaoRegistrada::dataEnvio))
                .toList();

        Optional<BigDecimal> media = ordenadas.isEmpty()
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(ordenadas.stream().mapToInt(AvaliacaoRegistrada::nota).sum())
                        .divide(BigDecimal.valueOf(ordenadas.size()), 2, RoundingMode.HALF_UP));

        Map<LocalDate, Long> porDia = new LinkedHashMap<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            porDia.put(dia, 0L);
        }
        Map<String, Long> porUrgencia = new LinkedHashMap<>();
        URGENCIAS.forEach(urgencia -> porUrgencia.put(urgencia, 0L));

        for (AvaliacaoRegistrada avaliacao : ordenadas) {
            porDia.merge(avaliacao.dia(), 1L, Long::sum);
            porUrgencia.merge(avaliacao.urgencia(), 1L, Long::sum);
        }

        return new RelatorioSemanal(inicio, fim, ordenadas, media, porDia, porUrgencia);
    }

    public int total() {
        return avaliacoes.size();
    }
}
