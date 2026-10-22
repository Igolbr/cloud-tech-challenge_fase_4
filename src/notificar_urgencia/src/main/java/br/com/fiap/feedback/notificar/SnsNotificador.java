package br.com.fiap.feedback.notificar;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** Publica o aviso no tópico SNS; os administradores recebem por assinatura de e-mail. */
public class SnsNotificador implements Notificador {

    private final SnsClient sns;
    private final String topicoArn;

    public SnsNotificador(SnsClient sns, String topicoArn) {
        this.sns = sns;
        this.topicoArn = topicoArn;
    }

    @Override
    public void enviar(String assunto, String mensagem) {
        sns.publish(PublishRequest.builder()
                .topicArn(topicoArn)
                .subject(assunto)
                .message(mensagem)
                .build());
    }
}
