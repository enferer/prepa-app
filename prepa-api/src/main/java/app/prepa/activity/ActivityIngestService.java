package app.prepa.activity;

import app.prepa.athlete.Athlete;
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

    public ActivityIngestService(ActivityRepository activities) {
        this.activities = activities;
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
                Optional<Activity> existante = activities.findByAthleteIdAndDedupKey(athlete.getId(), cle);
                if (existante.isPresent()) {
                    // Une activite deja connue peut revenir enrichie de ses tours et de sa meteo.
                    if (enrichirSiPossible(existante.get(), brute)) {
                        misesAJour++;
                    } else {
                        doublons++;
                    }
                    continue;
                }
                activities.save(construire(athlete, brute, cle));
                importees++;
            } catch (RuntimeException e) {
                log.warn("Activite ignoree a l'ingestion", e);
                erreurs.add(e.getMessage());
            }
        }
        return new GarminDtos.ResultatIngestion(lot.size(), importees, doublons, misesAJour, erreurs);
    }

    /**
     * Identifiant Garmin s'il existe, empreinte logique sinon. Le prefixe evite qu'une
     * empreinte puisse un jour entrer en collision avec un identifiant.
     */
    private String cleDeDedup(GarminDtos.ActiviteBrute brute) {
        if (brute.getGarminActivityId() != null) {
            return "garmin:" + brute.getGarminActivityId();
        }
        String empreinte = brute.getStartedAtLocal() + "|" + brute.getDureeSec() + "|" + brute.getDistanceM();
        return "sha:" + Hashing.sha256(empreinte);
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
