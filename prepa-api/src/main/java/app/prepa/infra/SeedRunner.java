package app.prepa.infra;

import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteRepository;
import app.prepa.auth.ServiceKeyService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Amorcage du premier compte administrateur et de sa cle de service.
 *
 * <p>Sans cela, impossible de creer quoi que ce soit : la creation de comptes est reservee
 * au role ADMIN, et il n'y a pas d'inscription publique. Ne s'execute que si
 * {@code prepa.seed.enabled=true} et que la base est vide.
 */
@Component
@ConditionalOnProperty(prefix = "prepa.seed", name = "enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final AthleteRepository athletes;
    private final PasswordEncoder passwordEncoder;
    private final ServiceKeyService serviceKeys;
    private final String email;
    private final String motDePasse;
    private final String nom;
    private final String username;

    public SeedRunner(
            AthleteRepository athletes,
            PasswordEncoder passwordEncoder,
            ServiceKeyService serviceKeys,
            @org.springframework.beans.factory.annotation.Value("${prepa.seed.admin-email}") String email,
            @org.springframework.beans.factory.annotation.Value("${prepa.seed.admin-password}") String motDePasse,
            @org.springframework.beans.factory.annotation.Value("${prepa.seed.admin-name:Admin}") String nom,
            @org.springframework.beans.factory.annotation.Value("${prepa.seed.admin-username:admin}") String username) {
        this.athletes = athletes;
        this.passwordEncoder = passwordEncoder;
        this.serviceKeys = serviceKeys;
        this.email = email;
        this.motDePasse = motDePasse;
        this.nom = nom;
        this.username = username;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (athletes.count() > 0) {
            log.info("Seed ignore : la base contient deja des athletes");
            return;
        }
        Athlete admin = new Athlete(
                UUID.randomUUID(), email, username, passwordEncoder.encode(motDePasse), nom);
        admin.setRole("ADMIN");
        athletes.save(admin);

        ServiceKeyService.NouvelleCle cle = serviceKeys.creer(
                "bootstrap", Set.of(ServiceKeyService.SCOPE_READ, ServiceKeyService.SCOPE_COACH,
                        ServiceKeyService.SCOPE_INGEST), null);

        log.warn("""

                ==========================================================================
                 Compte admin cree : {} (nom d'utilisateur : {})
                 Cle de service (notee une seule fois, conserve-la maintenant) :
                 {}
                ==========================================================================
                """, email, username, cle.cle());
    }
}
