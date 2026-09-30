package br.com.central.api.local;

import br.com.central.api.comercial.Cliente;
import br.com.central.api.comercial.ClienteRepository;
import br.com.central.api.comercial.Periodicidade;
import br.com.central.api.comercial.Plano;
import br.com.central.api.comercial.PlanoRepository;
import br.com.central.api.comercial.PrecoPlano;
import br.com.central.api.comercial.PrecoPlanoRepository;
import br.com.central.api.comercial.Produto;
import br.com.central.api.comercial.ProdutoRepository;
import br.com.central.api.comercial.TipoCliente;
import br.com.central.api.operador.Operador;
import br.com.central.api.operador.OperadorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cliente, produto Servirea e contratação já provisionada, no profile {@code dev},
 * e só se o JDBC aponta para esta máquina. O id da paróquia é o mesmo do seed
 * do Servire, então "Acessar aplicativo" entra nessa base. Não roda em produção.
 */
@Component
@Profile("dev")
public class AmbienteLocal implements ApplicationRunner {

    /** Iguais ao seed do Servire. Não mudar um sem o outro. */
    public static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID CONTRATACAO_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final String SLUG = "paroquia-teste";
    public static final String EMAIL_OPERADOR = "gustavo2@teste.local";
    public static final String SENHA = "12345678";

    private static final Logger log = LoggerFactory.getLogger(AmbienteLocal.class);
    private static final String DIREITOS = "{\"plano\":{\"codigo\":\"PAROQUIA\",\"nome\":\"Paróquia\"}}";

    private final OperadorRepository operadores;
    private final ProdutoRepository produtos;
    private final PlanoRepository planos;
    private final PrecoPlanoRepository precos;
    private final ClienteRepository clientes;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final String url;

    public AmbienteLocal(OperadorRepository operadores, ProdutoRepository produtos, PlanoRepository planos,
                         PrecoPlanoRepository precos, ClienteRepository clientes, PasswordEncoder passwordEncoder,
                         JdbcTemplate jdbc, @Value("${spring.datasource.url:}") String url) {
        this.operadores = operadores;
        this.produtos = produtos;
        this.planos = planos;
        this.precos = precos;
        this.clientes = clientes;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.url = url;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bancoNestaMaquina(url)) {
            return;
        }
        garantirOperador();
        Integer jaTem = jdbc.queryForObject(
                "SELECT count(*) FROM contratacao WHERE slug_instancia = ?", Integer.class, SLUG);
        if (jaTem != null && jaTem > 0) {
            return;
        }
        Produto produto = produtos.findByCodigoIgnoreCase("SERVIREA").orElseGet(() -> {
            Produto novo = new Produto("SERVIREA", "Servirea");
            novo.setUrlBaseIntegracao("http://localhost:8080");
            return produtos.saveAndFlush(novo);
        });
        if (produto.getUrlBaseIntegracao() == null || produto.getUrlBaseIntegracao().isBlank()) {
            produto.setUrlBaseIntegracao("http://localhost:8080");
        }
        Plano plano = planos.findByProduto_IdOrderByNomeAsc(produto.getId()).stream()
                .filter(p -> "PAROQUIA".equals(p.getCodigo()))
                .findFirst()
                .orElseGet(() -> planos.saveAndFlush(new Plano(produto, "PAROQUIA", "Paróquia")));
        Integer precosDoPlano = jdbc.queryForObject(
                "SELECT count(*) FROM preco_plano WHERE plano_id = ?", Integer.class, plano.getId());
        if (precosDoPlano == null || precosDoPlano == 0) {
            precos.save(new PrecoPlano(plano.getId(), Periodicidade.MENSAL, new BigDecimal("150.00"), LocalDate.of(2026, 1, 1)));
        }
        Cliente cliente = clientes.findByDocumento("00000000000191")
                .orElseGet(() -> clientes.saveAndFlush(new Cliente(TipoCliente.PJ, "00000000000191", "Paróquia de Teste")));

        jdbc.update("""
                INSERT INTO contratacao (
                    id, cliente_id, produto_id, plano_id, periodicidade, valor, dia_vencimento, inicio,
                    situacao_comercial, situacao_provisionamento, idempotency_key, id_externo,
                    nome_instancia, slug_instancia, admin_nome, admin_email, versao_direitos, direitos_atuais,
                    ultimo_status_provisionamento)
                VALUES (?, ?, ?, ?, 'MENSAL', 150.00, 10, CURRENT_DATE,
                    'ATIVA', 'ATIVA', ?, ?,
                    'Paróquia de Teste', ?, 'Administrador da paróquia', 'paroquia@teste.local', 1, ?::jsonb,
                    201)
                """, CONTRATACAO_ID, cliente.getId(), produto.getId(), plano.getId(),
                CONTRATACAO_ID, TENANT_ID, SLUG, DIREITOS);
        jdbc.update("""
                INSERT INTO cobranca (contratacao_id, competencia_inicio, competencia_fim, vencimento, valor, status)
                VALUES (?, date_trunc('month', CURRENT_DATE)::date,
                    (date_trunc('month', CURRENT_DATE) + interval '1 month - 1 day')::date,
                    (date_trunc('month', CURRENT_DATE) + interval '9 days')::date,
                    150.00, 'ABERTA')
                """, CONTRATACAO_ID);
        log.info("Ambiente local da Central: operador {} / {}. Cliente Paróquia de Teste, contratação {}",
                EMAIL_OPERADOR, SENHA, SLUG);
    }

    private void garantirOperador() {
        if (operadores.findByEmail(EMAIL_OPERADOR).isPresent()) {
            return;
        }
        operadores.save(new Operador("Operador", EMAIL_OPERADOR, passwordEncoder.encode(SENHA)));
    }

    /**
     * O host do JDBC precisa ser esta máquina. Olha o host de verdade, e não se o texto contém "localhost":
     * {@code ...supabase.com/postgres?app=localhost} ou {@code localhost.exemplo.com} não contam.
     */
    static boolean bancoNestaMaquina(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            return false;
        }
        try {
            String host = java.net.URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
