package br.com.fiap.feedback.receber;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceberAvaliacaoHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Avaliacao> gravadas = new ArrayList<>();
    private final ReceberAvaliacaoHandler handler = new ReceberAvaliacaoHandler(
            new ValidadorAvaliacao(mapper,
                    Clock.fixed(Instant.parse("2026-10-06T15:00:00Z"), ZoneOffset.UTC),
                    ZoneId.of("America/Sao_Paulo"),
                    () -> "abc-123"),
            gravadas::add);

    @Test
    void gravaEResponde201() throws Exception {
        APIGatewayV2HTTPResponse resposta = handler.handleRequest(evento("{\"descricao\":\"Conteúdo confuso\",\"nota\":2}"), null);

        assertEquals(201, resposta.getStatusCode());
        assertEquals(1, gravadas.size());
        assertEquals(Urgencia.CRITICA, gravadas.get(0).urgencia());

        JsonNode corpo = mapper.readTree(resposta.getBody());
        assertEquals("abc-123", corpo.get("id").asText());
        assertEquals("CRITICA", corpo.get("urgencia").asText());
        assertEquals("2026-10-06T15:00:00Z", corpo.get("dataEnvio").asText());
    }

    @Test
    void aceitaCorpoEmBase64() {
        APIGatewayV2HTTPEvent evento = evento(Base64.getEncoder().encodeToString(
                "{\"descricao\":\"Boa\",\"nota\":8}".getBytes(StandardCharsets.UTF_8)));
        evento.setIsBase64Encoded(true);

        assertEquals(201, handler.handleRequest(evento, null).getStatusCode());
    }

    @Test
    void responde400SemGravarQuandoInvalido() throws Exception {
        APIGatewayV2HTTPResponse resposta = handler.handleRequest(evento("{\"descricao\":\"x\",\"nota\":15}"), null);

        assertEquals(400, resposta.getStatusCode());
        assertTrue(gravadas.isEmpty());
        assertTrue(mapper.readTree(resposta.getBody()).get("erros").isArray());
    }

    @Test
    void propagaFalhaDeGravacao() {
        ReceberAvaliacaoHandler comFalha = new ReceberAvaliacaoHandler(
                new ValidadorAvaliacao(mapper, ZoneId.of("America/Sao_Paulo")),
                avaliacao -> { throw new IllegalStateException("DynamoDB indisponível"); });

        assertThrows(IllegalStateException.class,
                () -> comFalha.handleRequest(evento("{\"descricao\":\"x\",\"nota\":5}"), null));
    }

    private static APIGatewayV2HTTPEvent evento(String corpo) {
        APIGatewayV2HTTPEvent evento = new APIGatewayV2HTTPEvent();
        evento.setBody(corpo);
        return evento;
    }
}
