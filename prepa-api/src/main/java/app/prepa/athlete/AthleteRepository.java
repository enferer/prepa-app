package app.prepa.athlete;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AthleteRepository extends JpaRepository<Athlete, UUID> {

    Optional<Athlete> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Athlete> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Retrouve un athlete par l'un ou l'autre de ses identifiants de connexion.
     *
     * <p>Une seule requete plutot que deux enchainees : on ne veut pas que le temps de reponse
     * apprenne au visiteur si la saisie ressemblait a un email connu. Les deux colonnes ne
     * peuvent pas se croiser — un email porte toujours un arobase, qu'un nom d'utilisateur
     * n'accepte pas — donc la requete ramene au plus une ligne.
     */
    @Query("""
            select a from Athlete a
            where lower(a.email) = lower(:identifiant) or lower(a.username) = lower(:identifiant)
            """)
    Optional<Athlete> findByIdentifiant(@Param("identifiant") String identifiant);
}
