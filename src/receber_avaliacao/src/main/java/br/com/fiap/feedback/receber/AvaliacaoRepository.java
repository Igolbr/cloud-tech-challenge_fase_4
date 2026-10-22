package br.com.fiap.feedback.receber;

/** Persistência das avaliações recebidas. */
public interface AvaliacaoRepository {

    void salvar(Avaliacao avaliacao);
}
