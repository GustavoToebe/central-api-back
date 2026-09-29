package br.com.central.api.integracao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegracaoNonceServiceTest {

    @Mock
    private IntegracaoNonceRepository repository;

    @InjectMocks
    private IntegracaoNonceService service;

    @Test
    void registrarDevolveTrueQuandoONonceFoiGravado() {
        when(repository.registrar("chave-1", "nonce-1")).thenReturn(1);

        assertThat(service.registrar("chave-1", "nonce-1")).isTrue();
    }

    @Test
    void registrarDevolveFalseQuandoONonceJaExistia() {
        when(repository.registrar("chave-1", "nonce-1")).thenReturn(0);

        assertThat(service.registrar("chave-1", "nonce-1")).isFalse();
    }

    @Test
    void limparApagaNoncesComMaisDeDezMinutos() {
        Instant antes = Instant.now();
        service.limpar();
        Instant depois = Instant.now();

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(repository).apagarAnterioresA(limite.capture());
        assertThat(limite.getValue())
                .isBetween(antes.minus(Duration.ofMinutes(10)), depois.minus(Duration.ofMinutes(10)));
    }
}
