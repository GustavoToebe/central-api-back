package br.com.central.api.operador;

import br.com.servirea.comum.seguranca.Totp;

import br.com.central.api.security.OpaqueTokenGenerator;
import br.com.central.api.web.ResourceNotFoundException;
import br.com.central.api.web.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** Todas as verificações/alterações usam o mesmo lock do operador que o login e o refresh. */
@Service
public class MfaService {
    private final OperadorRepository operadores;
    private final PasswordEncoder senhas;
    private final MfaCifra cifra;
    private final JdbcTemplate jdbc;
    private final RefreshTokenService refresh;
    private final OperadorAuditoria auditoria;
    private final SecureRandom random = new SecureRandom();
    public MfaService(OperadorRepository operadores, PasswordEncoder senhas, MfaCifra cifra,
                      JdbcTemplate jdbc, RefreshTokenService refresh, OperadorAuditoria auditoria) {
        this.operadores = operadores; this.senhas = senhas; this.cifra = cifra;
        this.jdbc = jdbc; this.refresh = refresh; this.auditoria = auditoria;
    }
    @Transactional(readOnly = true)
    public Status status(UUID id) {
        Operador op = operadores.findById(id).orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));
        return new Status(op.getMfaSegredo() != null, cifra.configurada(), jdbc.queryForObject(
                "select count(*) from operador_mfa_recuperacao where operador_id = ? and usado_em is null", Integer.class, id));
    }
    @Transactional
    public Preparacao preparar(UUID id, String senha, String ip) {
        Operador op = operador(id, senha);
        if (op.getMfaSegredo() != null) throw new MfaException(HttpStatus.CONFLICT, "MFA já está ativo.", "MFA_JA_ATIVO");
        String segredo = Totp.novoSegredo(); Instant ate = Instant.now().plusSeconds(600);
        op.prepararMfa(cifra.cifrar(id, segredo), ate);
        auditoria.registrar(id, "MFA_PREPARADO", null, ip);
        return new Preparacao(segredo, ate);
    }
    @Transactional
    public Recuperacao ativar(UUID id, String senha, String codigo, String ip) {
        Operador op = operador(id, senha);
        if (op.getMfaSegredo() != null || op.getMfaPendente() == null || !Instant.now().isBefore(op.getMfaPendenteAte()))
            throw new MfaException(HttpStatus.CONFLICT, "Prepare novamente a configuração do MFA.", "MFA_PREPARACAO_EXPIRADA");
        long passo = Totp.verificar(cifra.decifrar(id, op.getMfaPendente()), codigo, Instant.now(), -1);
        if (passo < 0) throw MfaException.invalido();
        op.ativarMfa(passo);
        jdbc.update("delete from operador_mfa_recuperacao where operador_id = ?", id);
        List<String> codigos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[16]; random.nextBytes(bytes);
            String bruto = HexFormat.of().formatHex(bytes); codigos.add(bruto);
            jdbc.update("insert into operador_mfa_recuperacao(operador_id, codigo_hash) values (?, ?)", id, OpaqueTokenGenerator.hash(bruto));
        }
        refresh.encerrarTodas(id);
        auditoria.registrar(id, "MFA_ATIVADO", null, ip);
        return new Recuperacao(List.copyOf(codigos));
    }
    @Transactional
    public void desativar(UUID id, String senha, String codigo, String ip) {
        Operador op = operador(id, senha);
        if (op.getMfaSegredo() == null) throw new MfaException(HttpStatus.CONFLICT, "MFA não está ativo.", "MFA_INATIVO");
        verificar(op, codigo);
        op.desativarMfa(); jdbc.update("delete from operador_mfa_recuperacao where operador_id = ?", id);
        refresh.encerrarTodas(id); auditoria.registrar(id, "MFA_DESATIVADO", null, ip);
    }
    /** Chamado após senha válida, dentro da transação e do lock do login. */
    public void verificar(Operador op, String codigo) {
        if (op.getMfaSegredo() == null) return;
        if (codigo == null || codigo.isBlank()) throw new MfaException(HttpStatus.UNAUTHORIZED,
                "Informe o código do aplicativo autenticador ou um código de recuperação.", "MFA_NECESSARIO");
        codigo = codigo.trim();
        if (codigo.matches("[0-9a-f]{32}")) {
            if (jdbc.update("update operador_mfa_recuperacao set usado_em = ? where operador_id = ? and codigo_hash = ? and usado_em is null",
                    Instant.now().atOffset(java.time.ZoneOffset.UTC), op.getId(), OpaqueTokenGenerator.hash(codigo)) == 1) return;
        } else {
            long passo = Totp.verificar(cifra.decifrar(op.getId(), op.getMfaSegredo()), codigo, Instant.now(), op.getMfaUltimoPasso());
            if (passo >= 0) {op.consumirPassoMfa(passo); return;}
        }
        throw MfaException.invalido();
    }
    private Operador operador(UUID id, String senha) {
        Operador op = operadores.buscarParaAlterar(id).orElseThrow(() -> new UnauthorizedException("Sessão inválida."));
        if (!op.isAtivo()) throw new UnauthorizedException("Sessão inválida.");
        if (!senhas.matches(senha, op.getSenhaHash())) throw new MfaException(HttpStatus.UNAUTHORIZED, "A senha atual não confere.", "MFA_SENHA_INVALIDA");
        return op;
    }
    public record Status(boolean ativo, boolean configurado, int codigosRestantes) {}
    public record Preparacao(String segredo, Instant expiraEm) {
        @Override public String toString() {return "Preparacao[segredo=***, expiraEm=" + expiraEm + "]";}
    }
    public record Recuperacao(List<String> codigos) {
        @Override public String toString() {return "Recuperacao[codigos=***]";}
    }
}
