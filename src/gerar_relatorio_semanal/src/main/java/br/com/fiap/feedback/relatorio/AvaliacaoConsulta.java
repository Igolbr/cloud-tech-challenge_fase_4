package br.com.fiap.feedback.relatorio;

import java.time.LocalDate;
import java.util.List;

/** Leitura das avaliações gravadas. */
public interface AvaliacaoConsulta {

    /** Retorna todas as avaliações enviadas no dia informado (no fuso da aplicação). */
    List<AvaliacaoRegistrada> buscarPorDia(LocalDate dia);
}
