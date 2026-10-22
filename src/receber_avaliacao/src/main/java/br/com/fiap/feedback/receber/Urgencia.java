package br.com.fiap.feedback.receber;

/**
 * Nível de urgência de uma avaliação, derivado da nota.
 *
 * <p>Regra: nota 0 a 3 = CRITICA, 4 a 6 = MEDIA, 7 a 10 = BAIXA.
 * Somente avaliações CRITICA disparam o e-mail de aviso aos administradores.
 */
public enum Urgencia {
    CRITICA,
    MEDIA,
    BAIXA;

    public static Urgencia classificar(int nota) {
        if (nota < Avaliacao.NOTA_MINIMA || nota > Avaliacao.NOTA_MAXIMA) {
            throw new IllegalArgumentException("Nota fora do intervalo 0 a 10: " + nota);
        }
        if (nota <= 3) {
            return CRITICA;
        }
        if (nota <= 6) {
            return MEDIA;
        }
        return BAIXA;
    }
}
