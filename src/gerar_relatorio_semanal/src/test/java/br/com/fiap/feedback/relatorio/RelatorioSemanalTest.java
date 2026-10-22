package br.com.fiap.feedback.relatorio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatorioSemanalTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 28);
    private static final LocalDate FIM = LocalDate.of(2026, 10, 4);

    @Test
    void calculaMediaComDuasCasasDecimais() {
        RelatorioSemanal relatorio = RelatorioSemanal.calcular(INICIO, FIM, List.of(
                avaliacao("a", 10, "BAIXA", "2026-09-28"),
                avaliacao("b", 2, "CRITICA", "2026-09-28"),
                avaliacao("c", 5, "MEDIA", "2026-10-01")));

        // (10 + 2 + 5) / 3 = 5,666... -> 5,67
        assertEquals(new BigDecimal("5.67"), relatorio.media().orElseThrow());
        assertEquals(3, relatorio.total());
    }

    @Test
    void contaPorDiaIncluindoDiasSemAvaliacao() {
        RelatorioSemanal relatorio = RelatorioSemanal.calcular(INICIO, FIM, List.of(
                avaliacao("a", 10, "BAIXA", "2026-09-28"),
                avaliacao("b", 2, "CRITICA", "2026-09-28"),
                avaliacao("c", 5, "MEDIA", "2026-10-01")));

        assertEquals(7, relatorio.quantidadePorDia().size());
        assertEquals(2L, relatorio.quantidadePorDia().get(LocalDate.of(2026, 9, 28)));
        assertEquals(0L, relatorio.quantidadePorDia().get(LocalDate.of(2026, 9, 29)));
        assertEquals(1L, relatorio.quantidadePorDia().get(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void contaPorUrgenciaIncluindoUrgenciasSemAvaliacao() {
        RelatorioSemanal relatorio = RelatorioSemanal.calcular(INICIO, FIM, List.of(
                avaliacao("a", 1, "CRITICA", "2026-09-28"),
                avaliacao("b", 2, "CRITICA", "2026-09-29")));

        assertEquals(List.of("CRITICA", "MEDIA", "BAIXA"), List.copyOf(relatorio.quantidadePorUrgencia().keySet()));
        assertEquals(2L, relatorio.quantidadePorUrgencia().get("CRITICA"));
        assertEquals(0L, relatorio.quantidadePorUrgencia().get("MEDIA"));
        assertEquals(0L, relatorio.quantidadePorUrgencia().get("BAIXA"));
    }

    @Test
    void semAvaliacoesNaoTemMedia() {
        RelatorioSemanal relatorio = RelatorioSemanal.calcular(INICIO, FIM, List.of());

        assertTrue(relatorio.media().isEmpty());
        assertEquals(0, relatorio.total());
    }

    @Test
    void ordenaAvaliacoesPorDataDeEnvio() {
        RelatorioSemanal relatorio = RelatorioSemanal.calcular(INICIO, FIM, List.of(
                avaliacao("depois", 5, "MEDIA", "2026-10-02"),
                avaliacao("antes", 5, "MEDIA", "2026-09-30")));

        assertEquals("antes", relatorio.avaliacoes().get(0).id());
    }

    static AvaliacaoRegistrada avaliacao(String id, int nota, String urgencia, String dia) {
        return new AvaliacaoRegistrada(id, "Avaliação " + id, nota, urgencia,
                Instant.parse(dia + "T15:00:00Z"), LocalDate.parse(dia));
    }
}
