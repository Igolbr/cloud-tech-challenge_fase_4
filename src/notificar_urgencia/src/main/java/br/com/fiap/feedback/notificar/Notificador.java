package br.com.fiap.feedback.notificar;

/** Canal de envio de avisos aos administradores. */
public interface Notificador {

    void enviar(String assunto, String mensagem);
}
