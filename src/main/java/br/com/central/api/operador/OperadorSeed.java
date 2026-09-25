package br.com.central.api.operador;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Só no profile dev, e só se o e-mail e a senha vierem por variável de ambiente. */
@Component
@Profile("dev")
public class OperadorSeed implements ApplicationRunner {

    private final OperadorRepository operadorRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String senha;

    public OperadorSeed(OperadorRepository operadorRepository,
                        PasswordEncoder passwordEncoder,
                        @Value("${central.operador.seed-email:}") String email,
                        @Value("${central.operador.seed-senha:}") String senha) {
        this.operadorRepository = operadorRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            return;
        }
        String normalizado = email.trim().toLowerCase();
        if (operadorRepository.findByEmail(normalizado).isPresent()) {
            return;
        }
        operadorRepository.save(new Operador("Operador", normalizado, passwordEncoder.encode(senha)));
    }
}
