package br.com.central.api.operador;

import br.com.central.api.web.ApiException;
import org.springframework.http.HttpStatus;

public class MfaException extends ApiException {
    public MfaException(HttpStatus status, String mensagem, String codigo) {super(status, mensagem, codigo);}
    static MfaException invalido() {return new MfaException(HttpStatus.UNAUTHORIZED, "Código de autenticação inválido ou já utilizado.", "MFA_INVALIDO");}
}
