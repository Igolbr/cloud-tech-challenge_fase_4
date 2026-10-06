package br.com.fiap.feedback.receber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Converte o corpo JSON de {@code POST /avaliacao} em uma {@link Avaliacao}.
 *
 * <p>Regras: {@code descricao} é texto não vazio (até 2000 caracteres) e {@code nota}
 * é um número inteiro de 0 a 10. Qualquer violação gera {@link ValidacaoException}
 * com a lista de erros encontrados.
 */
public class ValidadorAvaliacao {

    private final ObjectMapper mapper;
    private final Clock clock;
    private final ZoneId fusoHorario;
    private final Supplier<String> geradorId;

    public ValidadorAvaliacao(ObjectMapper mapper, Clock clock, ZoneId fusoHorario, Supplier<String> geradorId) {
        this.mapper = mapper;
        this.clock = clock;
        this.fusoHorario = fusoHorario;
        this.geradorId = geradorId;
    }

    public ValidadorAvaliacao(ObjectMapper mapper, ZoneId fusoHorario) {
        this(mapper, Clock.systemUTC(), fusoHorario, () -> UUID.randomUUID().toString());
    }

    public Avaliacao validar(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            throw new ValidacaoException(List.of("O corpo da requisição é obrigatório"));
        }

        JsonNode json;
        try {
            json = mapper.readTree(corpo);
        } catch (JsonProcessingException e) {
            throw new ValidacaoException(List.of("O corpo da requisição não é um JSON válido"));
        }
        if (json == null || !json.isObject()) {
            throw new ValidacaoException(List.of("O corpo da requisição deve ser um objeto JSON"));
        }

        List<String> erros = new ArrayList<>();
        String descricao = validarDescricao(json.get("descricao"), erros);
        Integer nota = validarNota(json.get("nota"), erros);
        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros);
        }

        Instant agora = clock.instant();
        return new Avaliacao(
                geradorId.get(),
                descricao,
                nota,
                Urgencia.classificar(nota),
                agora,
                agora.atZone(fusoHorario).toLocalDate());
    }

    private static String validarDescricao(JsonNode campo, List<String> erros) {
        if (campo == null || campo.isNull()) {
            erros.add("O campo 'descricao' é obrigatório");
            return null;
        }
        if (!campo.isTextual()) {
            erros.add("O campo 'descricao' deve ser um texto");
            return null;
        }
        String descricao = campo.asText().strip();
        if (descricao.isEmpty()) {
            erros.add("O campo 'descricao' não pode ser vazio");
            return null;
        }
        if (descricao.length() > Avaliacao.TAMANHO_MAXIMO_DESCRICAO) {
            erros.add("O campo 'descricao' deve ter no máximo "
                    + Avaliacao.TAMANHO_MAXIMO_DESCRICAO + " caracteres");
            return null;
        }
        return descricao;
    }

    private static Integer validarNota(JsonNode campo, List<String> erros) {
        if (campo == null || campo.isNull()) {
            erros.add("O campo 'nota' é obrigatório");
            return null;
        }
        // isInt() rejeita textos ("7"), decimais (7.5 ou 7.0) e valores fora do range de int
        if (!campo.isInt()) {
            erros.add("O campo 'nota' deve ser um número inteiro");
            return null;
        }
        int nota = campo.intValue();
        if (nota < Avaliacao.NOTA_MINIMA || nota > Avaliacao.NOTA_MAXIMA) {
            erros.add("O campo 'nota' deve estar entre 0 e 10");
            return null;
        }
        return nota;
    }
}
