package app.prepa;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base des tests d'integration : un Postgres reel et un MockMvc traversant la vraie chaine
 * de filtres de securite.
 *
 * <p>Le conteneur est demarre une fois pour toute la machine virtuelle et jamais arrete.
 * Le laisser a la charge de l'extension JUnit le ferait arreter a la fin de la premiere
 * classe de test, alors que le contexte Spring — lui, mis en cache et partage — continue de
 * pointer dessus : les classes suivantes echouaient sur des connexions fermees.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = demarrerPostgres();

    private static PostgreSQLContainer<?> demarrerPostgres() {
        PostgreSQLContainer<?> conteneur = new PostgreSQLContainer<>("postgres:16-alpine");
        conteneur.start();
        return conteneur;
    }

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected FilterChainProxy securityFilterChain;

    protected MockMvc mvc;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("prepa.auth.jwt-secret", () -> "secret-de-test-suffisamment-long-pour-hs256-0123456789");
        registry.add("prepa.crypto.key", () -> "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        registry.add("prepa.seed.enabled", () -> "false");
    }

    @BeforeEach
    void initMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }
}
