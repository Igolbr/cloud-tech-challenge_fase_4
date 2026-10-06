package br.com.fiap.feedback.relatorio;

import java.time.Instant;
import java.time.LocalDate;

/** Avaliação lida da tabela para compor o relatório. */
public record AvaliacaoRegistrada(
        String id,
        String descricao,
        int nota,
        String urgencia,
        Instant dataEnvio,
        LocalDate dia) {
}
