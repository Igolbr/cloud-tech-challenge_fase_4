package br.com.fiap.feedback.relatorio;

/** Canal de envio do relatório aos administradores. */
public interface Notificador {

    void enviar(String assunto, String mensagem);
}
