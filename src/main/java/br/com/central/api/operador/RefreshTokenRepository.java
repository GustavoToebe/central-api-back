package br.com.central.api.operador;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select t.operador.id from RefreshToken t where t.tokenHash = :hash")
    Optional<UUID> operadorDoToken(String hash);

    @Modifying
    @Query("update RefreshToken t set t.revogadoEm = :quando where t.operador.id = :operadorId and t.revogadoEm is null")
    int revogarTodosAtivos(UUID operadorId, Instant quando);

    /** Troca de senha: apaga em vez de revogar, para token antigo não disparar a detecção de reuso. */
    @Modifying
    @Query("delete from RefreshToken t where t.operador.id = :operadorId")
    int apagarTodos(UUID operadorId);
}
