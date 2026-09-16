package app.prepa.auth;

import app.prepa.infra.Hashing;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creation des cles de service. La valeur en clair n'est montree qu'une seule fois. */
@Service
public class ServiceKeyService {

    /** Scopes reconnus : lecture, ecriture de coach, ingestion Garmin. */
    public static final String SCOPE_READ = "read";

    public static final String SCOPE_COACH = "coach";
    public static final String SCOPE_INGEST = "ingest";

    private final ServiceKeyRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ServiceKeyService(ServiceKeyRepository repository) {
        this.repository = repository;
    }

    /** La cle en clair n'existe qu'ici : seule son empreinte est persistee. */
    public record NouvelleCle(UUID id, String nom, String cle, Set<String> scopes, UUID athleteId) {}

    @Transactional
    public NouvelleCle creer(String nom, Set<String> scopes, UUID athleteId) {
        byte[] octets = new byte[32];
        random.nextBytes(octets);
        String cle = "psk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        UUID id = UUID.randomUUID();
        repository.save(new ServiceKey(id, nom, Hashing.sha256(cle), scopes, athleteId));
        return new NouvelleCle(id, nom, cle, scopes, athleteId);
    }
}
