package br.com.fiap.feedback.notificar;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificarUrgenciaHandlerTest {

    private record Envio(String assunto, String mensagem) {}

    private final List<Envio> enviados = new ArrayList<>();
    private final NotificarUrgenciaHandler handler = new NotificarUrgenciaHandler(
            (assunto, mensagem) -> enviados.add(new Envio(assunto, mensagem)),
            ZoneId.of("America/Sao_Paulo"));

    @Test
    void enviaEmailComDescricaoUrgenciaEData() {
        StreamsEventResponse resposta = handler.handleRequest(
                evento(registro("1", "INSERT", "CRITICA", "Professor não apareceu")), null);

        assertTrue(resposta.getBatchItemFailures().isEmpty());
        assertEquals(1, enviados.size());
        String mensagem = enviados.get(0).mensagem();
        assertTrue(mensagem.contains("Descrição: Professor não apareceu"));
        assertTrue(mensagem.contains("Urgência: CRITICA"));
        // 2026-10-06T15:00Z = 12:00 em São Paulo
        assertTrue(mensagem.contains("Data de envio: 06/10/2026 12:00:00"));
    }

    @Test
    void ignoraAvaliacoesNaoCriticasEEventosQueNaoSaoInsercao() {
        handler.handleRequest(evento(
                registro("1", "INSERT", "MEDIA", "Ok"),
                registro("2", "INSERT", "BAIXA", "Ótima"),
                registro("3", "MODIFY", "CRITICA", "Editada")), null);

        assertTrue(enviados.isEmpty());
    }

    @Test
    void reportaFalhaAPartirDoRegistroQueFalhou() {
        List<String> enviadosComSucesso = new ArrayList<>();
        NotificarUrgenciaHandler comFalha = new NotificarUrgenciaHandler((assunto, mensagem) -> {
            if (mensagem.contains("falha")) {
                throw new IllegalStateException("SNS indisponível");
            }
            enviadosComSucesso.add(mensagem);
        }, ZoneId.of("America/Sao_Paulo"));

        StreamsEventResponse resposta = comFalha.handleRequest(evento(
                registro("1", "INSERT", "CRITICA", "primeira"),
                registro("2", "INSERT", "CRITICA", "falha"),
                registro("3", "INSERT", "CRITICA", "terceira")), null);

        assertEquals(1, enviadosComSucesso.size());
        assertEquals(1, resposta.getBatchItemFailures().size());
        assertEquals("seq-2", resposta.getBatchItemFailures().get(0).getItemIdentifier());
    }

    @Test
    void eventoVazioNaoFalha() {
        assertTrue(handler.handleRequest(new DynamodbEvent(), null).getBatchItemFailures().isEmpty());
    }

    private static DynamodbEvent evento(DynamodbEvent.DynamodbStreamRecord... registros) {
        DynamodbEvent evento = new DynamodbEvent();
        evento.setRecords(List.of(registros));
        return evento;
    }

    private static DynamodbEvent.DynamodbStreamRecord registro(String id, String tipo, String urgencia, String descricao) {
        StreamRecord dados = new StreamRecord();
        dados.setSequenceNumber("seq-" + id);
        dados.setNewImage(Map.of(
                "id", new AttributeValue().withS(id),
                "descricao", new AttributeValue().withS(descricao),
                "nota", new AttributeValue().withN("2"),
                "urgencia", new AttributeValue().withS(urgencia),
                "dataEnvio", new AttributeValue().withS("2026-10-06T15:00:00Z"),
                "dia", new AttributeValue().withS("2026-10-06")));

        DynamodbEvent.DynamodbStreamRecord registro = new DynamodbEvent.DynamodbStreamRecord();
        registro.setEventID("evt-" + id);
        registro.setEventName(tipo);
        registro.setDynamodb(dados);
        return registro;
    }
}
