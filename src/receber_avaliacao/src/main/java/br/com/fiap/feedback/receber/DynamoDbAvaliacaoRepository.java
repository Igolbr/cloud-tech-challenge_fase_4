package br.com.fiap.feedback.receber;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

/**
 * Grava a avaliação na tabela DynamoDB.
 *
 * <p>Atributos: {@code id} (chave), {@code descricao}, {@code nota}, {@code urgencia},
 * {@code dataEnvio} (ISO-8601 em UTC) e {@code dia} (yyyy-MM-dd, chave do índice por dia).
 * A gravação dispara o DynamoDB Streams, que aciona a função notificar-urgencia.
 */
public class DynamoDbAvaliacaoRepository implements AvaliacaoRepository {

    private final DynamoDbClient dynamoDb;
    private final String nomeTabela;

    public DynamoDbAvaliacaoRepository(DynamoDbClient dynamoDb, String nomeTabela) {
        this.dynamoDb = dynamoDb;
        this.nomeTabela = nomeTabela;
    }

    @Override
    public void salvar(Avaliacao avaliacao) {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.fromS(avaliacao.id()),
                "descricao", AttributeValue.fromS(avaliacao.descricao()),
                "nota", AttributeValue.fromN(Integer.toString(avaliacao.nota())),
                "urgencia", AttributeValue.fromS(avaliacao.urgencia().name()),
                "dataEnvio", AttributeValue.fromS(avaliacao.dataEnvio().toString()),
                "dia", AttributeValue.fromS(avaliacao.dia().toString()));

        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(nomeTabela)
                .item(item)
                // Garante que um id repetido nunca sobrescreva uma avaliação existente
                .conditionExpression("attribute_not_exists(id)")
                .build());
    }
}
