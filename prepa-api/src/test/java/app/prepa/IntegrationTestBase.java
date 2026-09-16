package app.prepa;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.web.FilterChainProxy;

/**
 * Base des tests d'integration : un Postgres reel via Testcontainers et un MockMvc
 * traversant la vraie chaine de filtres de securite.
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

    @org.testcontainers.junit.jupiter.Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected FilterChainProxy securityFilterChain;

    protected MockMvc mvc;

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("prepa.auth.jwt-secret", () -> "secret-de-test-suffisamment-long-pour-hs256-0123456789");
        registry.add("prepa.crypto.key", () -> "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
        registry.add("prepa.seed.enabled", () -> "false");
    }

    @BeforeEach
    void initMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
    }
}
