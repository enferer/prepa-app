package app.prepa.auth;

import app.prepa.infra.Hashing;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resout une cle de service en clair vers un {@link Principal}.
 *
 * <p>Separe du filtre a dessein : un filtre annote {@code @Transactional} se fait proxyfier
 * par CGLIB, ce qui casse l'initialisation de {@code GenericFilterBean}.
 */
@Service
public class ServiceKeyAuthenticator {

    /** On n'ecrit {@code last_used_at} qu'une fois par heure, pour ne pas ecrire a chaque appel. */
    private static final Duration PERIODE_TRACE = Duration.ofHours(1);

    private final ServiceKeyRepository repository;

    public ServiceKeyAuthenticator(ServiceKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<Principal> authentifier(String cleEnClair) {
        return repository.findByKeyHash(Hashing.sha256(cleEnClair))
                .filter(ServiceKey::estActive)
                .map(sk -> {
                    tracerUtilisation(sk);
                    return Principal.service(sk.getNom(), sk.getScopes(), sk.getAthleteId());
                });
    }

    private void tracerUtilisation(ServiceKey sk) {
        Instant dernier = sk.getLastUsedAt();
        if (dernier == null || dernier.isBefore(Instant.now().minus(PERIODE_TRACE))) {
            sk.marquerUtilisee();
            repository.save(sk);
        }
    }
}
