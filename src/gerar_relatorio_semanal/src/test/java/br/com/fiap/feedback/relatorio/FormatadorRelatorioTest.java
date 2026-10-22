package br.com.fiap.feedback.relatorio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static br.com.fiap.feedback.relatorio.RelatorioSemanalTest.avaliacao;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatadorRelatorioTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 28);
    private static final LocalDate FIM = LocalDate.of(2026, 10, 4);
    private final FormatadorRelatorio formatador = new FormatadorRelatorio(ZoneId.of("America/Sao_Paulo"));

    @Test
    void mensagemTrazTodosOsDadosExigidos() {
        RelatorioSemanal relatorio = RelatorioSemanal.calcular(INICIO, FIM, List.of(
                avaliacao("a", 10, "BAIXA", "2026-09-28"),
                avaliacao("b", 3, "CRITICA", "2026-09-30")));

        String mensagem = formatador.mensagem(relatorio);

        assertTrue(mensagem.contains("Período: 28/09/2026 a 04/10/2026"));
        assertTrue(mensagem.contains("Média das notas: 6,50"));
        assertTrue(mensagem.contains("28/09/2026: 1"));
        assertTrue(mensagem.contains("29/09/2026: 0"));
        assertTrue(mensagem.contains("CRITICA: 1"));
        assertTrue(mensagem.contains("MEDIA: 0"));
        // descrição | urgência | data de envio (15:00 UTC = 12:00 em São Paulo)
        assertTrue(mensagem.contains("Avaliação b | CRITICA | 30/09/2026 12:00"));
    }

    @Test
    void assuntoTrazOPeriodo() {
        assertEquals("Relatório semanal de avaliações - 28/09/2026 a 04/10/2026",
                formatador.assunto(RelatorioSemanal.calcular(INICIO, FIM, List.of())));
    }

    @Test
    void semanaSemAvaliacoes() {
        String mensagem = formatador.mensagem(RelatorioSemanal.calcular(INICIO, FIM, List.of()));

        assertTrue(mensagem.contains("sem avaliações no período"));
        assertTrue(mensagem.contains("Nenhuma avaliação recebida no período."));
    }

    @Test
    void truncaListaMuitoGrandeMantendoContagens() {
        List<AvaliacaoRegistrada> muitas = new ArrayList<>();
        for (int i = 0; i < FormatadorRelatorio.LIMITE_AVALIACOES_LISTADAS + 5; i++) {
            muitas.add(avaliacao("x" + i, 8, "BAIXA", "2026-09-29"));
        }

        String mensagem = formatador.mensagem(RelatorioSemanal.calcular(INICIO, FIM, muitas));

        assertTrue(mensagem.contains("Total de avaliações: 105"));
        assertTrue(mensagem.contains("... e mais 5 avaliações não listadas"));
        assertFalse(mensagem.contains("Avaliação x104 |"));
    }
}
