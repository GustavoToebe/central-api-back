package br.com.central.api.operador;
import br.com.central.api.web.ApiException;
import org.springframework.http.HttpStatus;
public class LimiteLoginException extends ApiException {
    private final long segundos;
    public LimiteLoginException(long segundos){super(HttpStatus.TOO_MANY_REQUESTS,"Muitas tentativas de acesso. Aguarde e tente novamente.");this.segundos=segundos;}
    public long segundos(){return segundos;}
    @Override public String getCodigo(){return "LOGIN_LIMITADO";}
}
