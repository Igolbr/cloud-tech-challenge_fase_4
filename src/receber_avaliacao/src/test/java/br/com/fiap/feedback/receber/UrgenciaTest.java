package br.com.fiap.feedback.receber;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrgenciaTest {

    @ParameterizedTest
    @CsvSource({
            "0, CRITICA", "3, CRITICA",
            "4, MEDIA", "6, MEDIA",
            "7, BAIXA", "10, BAIXA"
    })
    void classificaPelasFaixasDeNota(int nota, Urgencia esperada) {
        assertEquals(esperada, Urgencia.classificar(nota));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void rejeitaNotaForaDoIntervalo(int nota) {
        assertThrows(IllegalArgumentException.class, () -> Urgencia.classificar(nota));
    }
}
