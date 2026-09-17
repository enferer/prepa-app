package app.prepa.activity;

import app.prepa.athlete.Athlete;
import app.prepa.cycle.ConstatService;
import app.prepa.garmin.GarminDtos;
import app.prepa.infra.Hashing;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entree unique des activites dans le systeme, quelle que soit leur provenance.
 *
 * <p>L'operation est idempotente : rejouer le meme lot ne cree pas de doublon. La cle de
 * deduplication est l'identifiant Garmin quand il existe — c'est une cle naturelle, stable —
 * et sinon une empreinte de l'heure de depart, de la duree et de la distance, pour les
 * activites venues d'un export CSV qui ne porte aucun identifiant.
 */
@Service
public class ActivityIngestService {

    private static final Logger log = LoggerFactory.getLogger(ActivityIngestService.class);

    private final ActivityRepository activities;
    private final ConstatService constats;

    public ActivityIngestService(ActivityRepository activities, ConstatService constats) {
        this.activities = activities;
        this.constats = constats;
    }

    @Transactional
    public GarminDtos.ResultatIngestion ingerer(Athlete athlete, List<GarminDtos.ActiviteBrute> lot) {
        int importees = 0;
        int doublons = 0;
        int misesAJour = 0;
        List<String> erreurs = new ArrayList<>();

        for (GarminDtos.ActiviteBrute brute : lot) {
            try {
                String cle = cleDeDedup(brute);
                Optional<Activity> existante = retrouver(athlete, brute, cle);
                if (existante.isPresent()) {
                    // Une activite deja connue peut revenir enrichie de ses tours et de sa meteo.
                    if (enrichirSiPossible(existante.get(), brute)) {
                        misesAJour++;
                    } else {
                        doublons++;
                    }
                    continue;
                }
                Activity creee = activities.save(construire(athlete, brute, cle));
                // Une seance qui arrive de la montre est constatee tout de suite : l'athlete
                // la voit comptee le soir meme, sans attendre le point hebdomadaire.
                constats.constaterArrivee(creee);
                importees++;
            } catch (RuntimeException e) {
                log.warn("Activite ignoree a l'ingestion", e);
                erreurs.add(e.getMessage());
            }
        }
        return new GarminDtos.ResultatIngestion(lot.size(), importees, doublons, misesAJour, erreurs);
    }

    /**
     * Retrouve une activite deja connue.
     *
     * <p>L'identifiant Garmin prime : c'est une cle naturelle et stable. Mais une meme seance
     * peut arriver deux fois par des chemins differents — d'abord par un export CSV, qui ne
     * porte aucun identifiant, puis par l'API qui, elle, en a un. La recherche retombe donc
     * sur l'empreinte temporelle, et l'identifiant est pose au passage sur l'activite
     * existante plutot que d'en creer une seconde.
     */
    private Optional<Activity> retrouver(Athlete athlete, GarminDtos.ActiviteBrute brute, String cle) {
        if (brute.getGarminActivityId() != null) {
            Optional<Activity> parIdentifiant =
                    activities.findByAthleteIdAndGarminActivityId(athlete.getId(), brute.getGarminActivityId());
            if (parIdentifiant.isPresent()) {
                return parIdentifiant;
            }
        }
        return activities.findByAthleteIdAndDedupKey(athlete.getId(), cle);
    }

    /**
     * Empreinte de deduplication : l'heure de depart locale, a la seconde.
     *
     * <p>C'est la seule donnee que l'export CSV et l'API Garmin ecrivent a l'identique — la
     * distance, elle, est arrondie au dixieme de kilometre dans le CSV et donnee au metre par
     * l'API, si bien qu'une empreinte qui l'inclurait ferait diverger les deux sources.
     * On ne demarre pas deux seances a la meme seconde.
     */
    private String cleDeDedup(GarminDtos.ActiviteBrute brute) {
        return "t:" + Hashing.sha256(brute.getStartedAtLocal().toString());
    }

    private Activity construire(Athlete athlete, GarminDtos.ActiviteBrute brute, String cle) {
        ZoneId zone = ZoneId.of(athlete.getTimezone());
        Instant depart = brute.getStartedAtLocal().atZone(zone).toInstant();
        LocalDate dateLocale = brute.getStartedAtLocal().toLocalDate();

        Activity activite = new Activity(
                UUID.randomUUID(), athlete.getId(), cle, depart, dateLocale, brute.getDureeSec());
        activite.setGarminActivityId(brute.getGarminActivityId());
        activite.setSource(brute.getSource());
        activite.setType(brute.getType());
        activite.setTypeGarmin(brute.getTypeGarmin());
        activite.setTitre(brute.getTitre());
        activite.setDureeMouvementSec(brute.getDureeMouvementSec());
        activite.setDistanceM(brute.getDistanceM());
        activite.setAllureMoySecKm(brute.getAllureMoySecKm());
        activite.setMeilleureAllureSecKm(brute.getMeilleureAllureSecKm());
        activite.setGapMoySecKm(brute.getGapMoySecKm());
        activite.setFcMoy(brute.getFcMoy());
        activite.setFcMax(brute.getFcMax());
        activite.setFcMin(brute.getFcMin());
        activite.setCadenceMoy(brute.getCadenceMoy());
        activite.setCadenceMax(brute.getCadenceMax());
        activite.setDenivelePosM(brute.getDenivelePosM());
        activite.setDeniveleNegM(brute.getDeniveleNegM());
        activite.setAltitudeMinM(brute.getAltitudeMinM());
        activite.setAltitudeMaxM(brute.getAltitudeMaxM());
        activite.setCalories(brute.getCalories());
        activite.setTeAerobie(decimal(brute.getTeAerobie()));
        activite.setTeAnaerobie(decimal(brute.getTeAnaerobie()));
        activite.setTeLabel(brute.getTeLabel());
        activite.setChargeEntrainement(decimal(brute.getChargeEntrainement()));
        activite.setVo2max(decimal(brute.getVo2max()));
        activite.setLongueurFouleeM(decimal(brute.getLongueurFouleeM()));
        activite.setOscillationVerticale(decimal(brute.getOscillationVerticale()));
        activite.setTempsContactSol(brute.getTempsContactSol());
        activite.setPuissanceMoy(brute.getPuissanceMoy());
        activite.setPuissanceMax(brute.getPuissanceMax());
        activite.setLieu(brute.getLieu());
        activite.setMeteo(brute.getMeteo());
        activite.setZonesFc(brute.getZonesFc());
        if (brute.getTours() != null && !brute.getTours().isEmpty()) {
            activite.remplacerTours(construireTours(brute));
        }
        return activite;
    }

    /**
     * Complete une activite existante avec un detail qu'elle n'avait pas encore.
     * Renvoie vrai si quelque chose a change — c'est ce qui distingue une mise a jour
     * d'un simple doublon.
     */
    private boolean enrichirSiPossible(Activity activite, GarminDtos.ActiviteBrute brute) {
        boolean modifiee = false;
        if (brute.getTours() != null && !brute.getTours().isEmpty() && !activite.isADetail()) {
            activite.remplacerTours(construireTours(brute));
            modifiee = true;
        }
        if (activite.getMeteo() == null && brute.getMeteo() != null) {
            activite.setMeteo(brute.getMeteo());
            modifiee = true;
        }
        if (activite.getZonesFc() == null && brute.getZonesFc() != null) {
            activite.setZonesFc(brute.getZonesFc());
            modifiee = true;
        }
        if (activite.getGarminActivityId() == null && brute.getGarminActivityId() != null) {
            activite.setGarminActivityId(brute.getGarminActivityId());
            modifiee = true;
        }
        if (modifiee) {
            activities.save(activite);
        }
        return modifiee;
    }

    private List<ActivityLap> construireTours(GarminDtos.ActiviteBrute brute) {
        List<ActivityLap> tours = new ArrayList<>();
        for (GarminDtos.TourBrut t : brute.getTours()) {
            ActivityLap tour = new ActivityLap(UUID.randomUUID(), t.getIndex(), t.getDureeSec());
            tour.setDistanceM(t.getDistanceM());
            tour.setAllureSecKm(t.getAllureSecKm());
            tour.setGapSecKm(t.getGapSecKm());
            tour.setFcMoy(t.getFcMoy());
            tour.setFcMax(t.getFcMax());
            tour.setCadenceMoy(t.getCadenceMoy());
            tour.setPuissanceMoy(t.getPuissanceMoy());
            tour.setDenivelePosM(t.getDenivelePosM());
            tour.setDeniveleNegM(t.getDeniveleNegM());
            tour.setIntensite(t.getIntensite());
            tours.add(tour);
        }
        return tours;
    }

    private static BigDecimal decimal(Double valeur) {
        return valeur == null ? null : BigDecimal.valueOf(valeur);
    }
}
