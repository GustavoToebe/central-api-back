package br.com.central.api.contrato;

import br.com.central.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T17: contrato versionado da API. Gera, a partir dos controllers e dos DTOs (records), o inventário de rotas com a
 * permissão exigida e o formato de pedido e resposta, e compara com docs/contrato-api.json. Mudar rota, permissão ou
 * campo sem atualizar o arquivo derruba a CI: a mudança de contrato passa a ser deliberada e revisável. Para atualizar:
 * {@code mvn test -Dtest=ContratoApiTest -Dcontrato.atualizar=true} e commitar o arquivo (e os clientes gerados dele).
 */
class ContratoApiTest extends AbstractIntegrationTest {
    private static final Path ARQUIVO = Path.of("docs/contrato-api.json");
    private static final String PACOTE = "br.com.central.api.";

    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapeamento;

    @Test
    void contratoDaApiCoincideComODocumentoVersionado() throws Exception {
        String atual = gerar();
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/contrato-api.atual.json"), atual, StandardCharsets.UTF_8);
        if (Boolean.getBoolean("contrato.atualizar")) {
            Files.writeString(ARQUIVO, atual, StandardCharsets.UTF_8);
        }
        assertThat(Files.exists(ARQUIVO)).as("docs/contrato-api.json existe (rode com -Dcontrato.atualizar=true)").isTrue();
        String versionado = Files.readString(ARQUIVO, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(atual.replace("\r\n", "\n"))
                .as("A API mudou. Revise e atualize docs/contrato-api.json com -Dcontrato.atualizar=true")
                .isEqualTo(versionado);
    }

    /** Rotas sem @PreAuthorize: login/refresh/logout do operador e o webhook do Mercado Pago (validado por assinatura). */
    private static final Set<String> SEM_PERMISSAO_POR_DESENHO = Set.of(
            "POST /auth/login", "POST /auth/logout", "POST /auth/refresh", "POST /webhooks/mercadopago");

    @Test
    void rotaNovaDeveDeclararPermissaoOuEntrarNaListaDePublicasPorDesenho() throws Exception {
        var mapa = new ObjectMapper().readTree(gerar());
        Set<String> semPermissao = new TreeSet<>();
        for (var rota : mapa.path("rotas")) {
            if (rota.path("autorizacao").isNull() || rota.path("autorizacao").asString().isEmpty()) {
                semPermissao.add(rota.path("metodo").asString() + " " + rota.path("caminho").asString());
            }
        }
        assertThat(semPermissao).as("Rotas sem @PreAuthorize: só as públicas ou da própria sessão, conscientemente").isEqualTo(new TreeSet<>(SEM_PERMISSAO_POR_DESENHO));
    }

    // ---- Geração

    String gerar() throws Exception {
        List<Map<String, Object>> rotas = new ArrayList<>();
        SortedMap<String, Object> esquemas = new TreeMap<>();
        for (var entrada : mapeamento.getHandlerMethods().entrySet()) {
            var info = entrada.getKey();
            HandlerMethod metodo = entrada.getValue();
            if (!metodo.getBeanType().getName().startsWith("br.com.central.api.")) {
                continue;
            }
            var caminhos = info.getPathPatternsCondition() == null ? Set.<String>of()
                    : new TreeSet<>(info.getPathPatternsCondition().getPatternValues());
            var verbos = info.getMethodsCondition().getMethods().isEmpty() ? List.of("ANY")
                    : info.getMethodsCondition().getMethods().stream().map(Enum::name).sorted().toList();
            String autorizacao = autorizacao(metodo.getMethod());
            String corpo = null;
            for (Parameter p : metodo.getMethod().getParameters()) {
                if (p.isAnnotationPresent(RequestBody.class)) {
                    corpo = tipo(p.getParameterizedType(), esquemas);
                }
            }
            String resposta = tipo(retorno(metodo.getMethod()), esquemas);
            for (String caminho : caminhos) {
                for (String verbo : verbos) {
                    Map<String, Object> rota = new LinkedHashMap<>();
                    rota.put("metodo", verbo);
                    rota.put("caminho", caminho);
                    rota.put("autorizacao", autorizacao);
                    rota.put("corpo", corpo);
                    rota.put("resposta", resposta);
                    rota.put("controlador", metodo.getBeanType().getSimpleName() + "#" + metodo.getMethod().getName());
                    rotas.add(rota);
                }
            }
        }
        rotas.sort(Comparator.comparing((Map<String, Object> r) -> (String) r.get("caminho"))
                .thenComparing(r -> (String) r.get("metodo")).thenComparing(r -> (String) r.get("controlador")));
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("produto", "Central");
        raiz.put("rotas", rotas);
        raiz.put("esquemas", esquemas);
        JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        return json.writeValueAsString(raiz) + "\n";
    }

    private static String autorizacao(Method m) {
        PreAuthorize p = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
        if (p == null) {
            p = AnnotatedElementUtils.findMergedAnnotation(m.getDeclaringClass(), PreAuthorize.class);
        }
        return p == null ? null : p.value();
    }

    private static Type retorno(Method m) {
        Type t = m.getGenericReturnType();
        if (t instanceof ParameterizedType pt && pt.getRawType() == ResponseEntity.class) {
            return pt.getActualTypeArguments()[0];
        }
        return t;
    }

    /** Nome legível do tipo; registra em {@code esquemas} os records do projeto que aparecem (recursivamente). */
    private String tipo(Type t, SortedMap<String, Object> esquemas) {
        if (t == void.class || t == Void.class) {
            return null;
        }
        if (t instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            if (Collection.class.isAssignableFrom(raw)) {
                return tipo(pt.getActualTypeArguments()[0], esquemas) + "[]";
            }
            if (Map.class.isAssignableFrom(raw)) {
                return "Record<string," + tipo(pt.getActualTypeArguments()[1], esquemas) + ">";
            }
            List<String> args = new ArrayList<>();
            for (Type a : pt.getActualTypeArguments()) {
                args.add(tipo(a, esquemas));
            }
            String base = nome(raw, esquemas);
            return base + "<" + String.join(",", args) + ">";
        }
        if (t instanceof Class<?> c) {
            if (c.isArray()) {
                return tipo(c.getComponentType(), esquemas) + "[]";
            }
            return nome(c, esquemas);
        }
        if (t instanceof TypeVariable<?> v) {
            return v.getName();
        }
        if (t instanceof WildcardType w) {
            return tipo(w.getUpperBounds()[0], esquemas);
        }
        return "unknown";
    }

    private String nome(Class<?> c, SortedMap<String, Object> esquemas) {
        if (c == String.class || c == UUID.class) {
            return "string";
        }
        if (c == boolean.class || c == Boolean.class) {
            return "boolean";
        }
        if (c.isPrimitive() || Number.class.isAssignableFrom(c)) {
            return "number";
        }
        if (c.getName().startsWith("java.time.") || c == Date.class) {
            return "string";
        }
        if (c == Object.class || c.getName().startsWith("tools.jackson.") || c.getName().startsWith("com.fasterxml.")) {
            return "unknown";
        }
        if (c == byte[].class || c.getName().startsWith("org.springframework.")) {
            return "unknown";
        }
        if (c.isEnum()) {
            String id = identificador(c);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("enum", Arrays.stream(c.getEnumConstants()).map(Object::toString).toList());
            esquemas.putIfAbsent(id, e);
            return id;
        }
        if (c.getName().startsWith(PACOTE)) {
            String id = identificador(c);
            if (!esquemas.containsKey(id)) {
                esquemas.put(id, "(em geração)");
                Map<String, Object> campos = new LinkedHashMap<>();
                if (c.isRecord()) {
                    for (RecordComponent rc : c.getRecordComponents()) {
                        campos.put(rc.getName(), tipo(rc.getGenericType(), esquemas) + (obrigatorio(rc) ? "" : "?"));
                    }
                } else {
                    for (Method m : c.getMethods()) {
                        if (m.getParameterCount() == 0 && m.getName().startsWith("get") && m.getDeclaringClass() != Object.class && !m.getName().equals("getClass")) {
                            String n = Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                            campos.put(n, tipo(m.getGenericReturnType(), esquemas) + "?");
                        }
                    }
                }
                esquemas.put(id, new TreeMap<>(campos));
            }
            return id;
        }
        return "unknown";
    }

    /** Nome único do tipo: pacote relativo e classes externas, para dois records "Autorizacao" não colidirem. */
    private static String identificador(Class<?> c) {
        return c.getName().startsWith(PACOTE) ? c.getName().substring(PACOTE.length()).replace('$', '.') : c.getName().replace('$', '.');
    }

    private static boolean obrigatorio(RecordComponent rc) {
        for (var a : rc.getAnnotations()) {
            String n = a.annotationType().getSimpleName();
            if (n.equals("NotNull") || n.equals("NotBlank") || n.equals("NotEmpty")) {
                return true;
            }
        }
        return rc.getType().isPrimitive();
    }
}
