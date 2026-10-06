package br.com.fiap.feedback.receber;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Avaliação de aula já validada, pronta para ser gravada.
 *
 * <p>Não contém dados pessoais do aluno: apenas descrição, nota, urgência e data de envio.
 *
 * @param id        identificador único (UUID)
 * @param descricao texto livre enviado pelo aluno
 * @param nota      nota inteira de 0 a 10
 * @param urgencia  urgência calculada a partir da nota
 * @param dataEnvio instante em que a avaliação foi recebida (UTC)
 * @param dia       dia do envio no fuso da aplicação; chave do índice usado pelo relatório semanal
 */
public record Avaliacao(
        String id,
        String descricao,
        int nota,
        Urgencia urgencia,
        Instant dataEnvio,
        LocalDate dia) {

    public static final int NOTA_MINIMA = 0;
    public static final int NOTA_MAXIMA = 10;
    public static final int TAMANHO_MAXIMO_DESCRICAO = 2000;
}
