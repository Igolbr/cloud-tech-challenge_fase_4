package br.com.fiap.feedback.relatorio;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.fiap.feedback.relatorio.RelatorioSemanalTest.avaliacao;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GerarRelatorioSemanalHandlerTest {

    @Test
    void consultaOsSeteDiasAnterioresEEnviaOEmail() {
        List<LocalDate> diasConsultados = new ArrayList<>();
        List<String> mensagens = new ArrayList<>();
        AvaliacaoConsulta consulta = dia -> {
            diasConsultados.add(dia);
            return dia.equals(LocalDate.of(2026, 10, 1))
                    ? List.of(avaliacao("a", 4, "MEDIA", "2026-10-01"), avaliacao("b", 8, "BAIXA", "2026-10-01"))
                    : List.of();
        };

        // Segunda-feira, 05/10/2026 às 08:00 em São Paulo
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T11:00:00Z"), ZoneOffset.UTC);
        GerarRelatorioSemanalHandler handler = new GerarRelatorioSemanalHandler(
                consulta, (assunto, mensagem) -> mensagens.add(assunto + "\n" + mensagem),
                clock, ZoneId.of("America/Sao_Paulo"));

        String resumo = handler.handleRequest(Map.of(), null);

        assertEquals(7, diasConsultados.size());
        assertEquals(LocalDate.of(2026, 9, 28), diasConsultados.get(0));
        assertEquals(LocalDate.of(2026, 10, 4), diasConsultados.get(6));
        assertEquals(1, mensagens.size());
        assertTrue(mensagens.get(0).contains("Média das notas: 6,00"));
        assertTrue(resumo.contains("2 avaliações"));
    }
}
