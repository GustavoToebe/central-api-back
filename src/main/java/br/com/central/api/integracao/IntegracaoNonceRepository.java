package br.com.central.api.integracao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface IntegracaoNonceRepository extends JpaRepository<IntegracaoNonce, IntegracaoNonce.Chave> {

    /**
     * 1 = gravou, 0 = já existia. {@code ON CONFLICT} porque {@code save}
     * com id preenchido faria merge e o nonce repetido passaria.
     */
    @Modifying
    @Query(value = "INSERT INTO public.integracao_nonce (chave_id, nonce, recebido_em) "
            + "VALUES (:chaveId, :nonce, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int registrar(String chaveId, String nonce);

    @Modifying
    @Query("delete from IntegracaoNonce n where n.recebidoEm < :limite")
    int apagarAnterioresA(Instant limite);
}
