package app.prepa.athlete;

import app.prepa.infra.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profil sportif : donnees de fond de l'athlete, independantes du cycle en cours. */
@Service
public class AthleteProfileService {

    private final AthleteProfileRepository profils;
    private final PersonalRecordRepository records;
    private final InjuryRepository blessures;
    private final AthleteConstraintRepository contraintes;
    private final SignatureSessionRepository signatures;

    public AthleteProfileService(
            AthleteProfileRepository profils,
            PersonalRecordRepository records,
            InjuryRepository blessures,
            AthleteConstraintRepository contraintes,
            SignatureSessionRepository signatures) {
        this.profils = profils;
        this.records = records;
        this.blessures = blessures;
        this.contraintes = contraintes;
        this.signatures = signatures;
    }

    /** Le profil existe toujours : on le cree vide a la premiere demande. */
    @Transactional
    public AthleteProfile profil(UUID athleteId) {
        return profils.findById(athleteId).orElseGet(() -> profils.save(new AthleteProfile(athleteId)));
    }

    @Transactional
    public AthleteProfile mettreAJour(UUID athleteId, ProfileDtos.UpdateProfileRequest req) {
        AthleteProfile profil = profil(athleteId);
        if (req.fcMax() != null) {
            profil.setFcMax(req.fcMax());
        }
        if (req.fcRepos() != null) {
            profil.setFcRepos(req.fcRepos());
        }
        if (req.vmaKmh() != null) {
            profil.setVmaKmh(req.vmaKmh());
        }
        if (req.volumeHabituelKm() != null) {
            profil.setVolumeHabituelKm(req.volumeHabituelKm());
        }
        if (req.joursDisponibles() != null) {
            profil.setJoursDisponibles(req.joursDisponibles().toArray(String[]::new));
        }
        if (req.renfoActif() != null) {
            profil.setRenfoActif(req.renfoActif());
        }
        if (req.renfoFrequence() != null) {
            profil.setRenfoFrequence(req.renfoFrequence());
        }
        if (req.renfoMateriel() != null) {
            profil.setRenfoMateriel(req.renfoMateriel().toArray(String[]::new));
        }
        if (req.renfoFocus() != null) {
            profil.setRenfoFocus(req.renfoFocus());
        }
        if (req.notes() != null) {
            profil.setNotes(req.notes());
        }
        return profils.save(profil);
    }

    @Transactional(readOnly = true)
    public List<PersonalRecord> records(UUID athleteId) {
        return records.findByAthleteIdOrderByDateDesc(athleteId);
    }

    @Transactional
    public PersonalRecord ajouterRecord(UUID athleteId, ProfileDtos.RecordRequest req) {
        PersonalRecord record = new PersonalRecord(
                UUID.randomUUID(), athleteId, req.distanceM(), req.tempsSec(), req.date());
        record.setContexte(req.contexte());
        return records.save(record);
    }

    @Transactional
    public void supprimerRecord(UUID athleteId, UUID recordId) {
        records.delete(verifier(records.findById(recordId).orElseThrow(() -> ApiException.notFound("Record")),
                r -> r.getAthleteId(), athleteId));
    }

    @Transactional(readOnly = true)
    public List<Injury> blessures(UUID athleteId) {
        return blessures.findByAthleteIdOrderByDebutDesc(athleteId);
    }

    @Transactional(readOnly = true)
    public List<Injury> blessuresEnCours(UUID athleteId) {
        return blessures.findByAthleteIdAndStatutNot(athleteId, "RESOLUE");
    }

    @Transactional
    public Injury ajouterBlessure(UUID athleteId, ProfileDtos.InjuryRequest req) {
        Injury blessure = new Injury(UUID.randomUUID(), athleteId, req.zone(), req.debut());
        appliquer(blessure, req);
        return blessures.save(blessure);
    }

    @Transactional
    public Injury modifierBlessure(UUID athleteId, UUID injuryId, ProfileDtos.InjuryRequest req) {
        Injury blessure = verifier(
                blessures.findById(injuryId).orElseThrow(() -> ApiException.notFound("Blessure")),
                Injury::getAthleteId, athleteId);
        blessure.setZone(req.zone());
        blessure.setDebut(req.debut());
        appliquer(blessure, req);
        return blessures.save(blessure);
    }

    @Transactional(readOnly = true)
    public List<AthleteConstraint> contraintes(UUID athleteId) {
        return contraintes.findByAthleteId(athleteId);
    }

    @Transactional
    public AthleteConstraint ajouterContrainte(UUID athleteId, ProfileDtos.ConstraintRequest req) {
        AthleteConstraint contrainte =
                new AthleteConstraint(UUID.randomUUID(), athleteId, req.type(), req.detail());
        contrainte.setDebut(req.debut());
        contrainte.setFin(req.fin());
        return contraintes.save(contrainte);
    }

    @Transactional
    public void supprimerContrainte(UUID athleteId, UUID id) {
        contraintes.delete(verifier(
                contraintes.findById(id).orElseThrow(() -> ApiException.notFound("Contrainte")),
                AthleteConstraint::getAthleteId, athleteId));
    }

    @Transactional(readOnly = true)
    public List<SignatureSession> seancesSignature(UUID athleteId) {
        return signatures.findByAthleteId(athleteId);
    }

    @Transactional
    public SignatureSession ajouterSignature(UUID athleteId, ProfileDtos.SignatureSessionRequest req) {
        SignatureSession seance =
                new SignatureSession(UUID.randomUUID(), athleteId, req.nom(), req.typeSeance());
        appliquer(seance, req);
        return signatures.save(seance);
    }

    @Transactional
    public SignatureSession modifierSignature(
            UUID athleteId, UUID id, ProfileDtos.SignatureSessionRequest req) {
        SignatureSession seance = verifier(
                signatures.findById(id).orElseThrow(() -> ApiException.notFound("Seance signature")),
                SignatureSession::getAthleteId, athleteId);
        seance.setNom(req.nom());
        seance.setTypeSeance(req.typeSeance());
        appliquer(seance, req);
        return signatures.save(seance);
    }

    private static void appliquer(Injury blessure, ProfileDtos.InjuryRequest req) {
        if (req.statut() != null) {
            blessure.setStatut(req.statut());
        }
        blessure.setPalier(req.palier());
        blessure.setConsignes(req.consignes());
        blessure.setFin(req.fin());
    }

    private static void appliquer(SignatureSession seance, ProfileDtos.SignatureSessionRequest req) {
        seance.setDescription(req.description());
        seance.setDistanceKm(req.distanceKm());
        seance.setFrequenceSouhaitee(req.frequenceSouhaitee());
        seance.setContexte(req.contexte());
        if (req.actif() != null) {
            seance.setActif(req.actif());
        }
    }

    /** Garde-fou : un identifiant valide ne doit pas donner acces aux donnees d'un autre athlete. */
    private static <T> T verifier(T entite, java.util.function.Function<T, UUID> proprietaire, UUID athleteId) {
        if (!athleteId.equals(proprietaire.apply(entite))) {
            throw ApiException.notFound("Ressource");
        }
        return entite;
    }
}
