package app.prepa.analysis;

import app.prepa.activity.Activity;
import app.prepa.activity.ActivityRepository;
import app.prepa.cycle.Cycle;
import app.prepa.cycle.PlannedSession;
import app.prepa.cycle.PlannedSessionRepository;
import app.prepa.cycle.TrainingWeek;
import app.prepa.domain.StatutSeance;
import app.prepa.journal.JournalEntry;
import app.prepa.journal.JournalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rapprochement d'une semaine : ce qui etait prevu face a ce qui a ete fait.
 *
 * <p>Le travail que le coach faisait en croisant quatre fichiers a la main est fait ici, et
 * les ecarts en ressortent <em>deja qualifies</em>. Ce sont exactement les cinq situations
 * que la methodologie impose de ne jamais adapter en silence : un ecart de distance de plus
 * de vingt pour cent, une seance cle sautee, une frequence cardiaque anormale, un volume
 * hebdomadaire loin de sa cible, une douleur signalee au journal.
 *
 * <p>Le service ne decide rien : il presente. C'est au coach de trancher, apres avoir
 * demande la cause.
 */
@Service
public class ReconciliationService {

    /** Au-dela de cet ecart entre prevu et realise, on questionne plutot qu'on adapte. */
    private static final double ECART_DISTANCE_SIGNIFICATIF = 0.20;

    /** En dessous de cette part de la cible, le volume de la semaine merite une explication. */
    private static final double PART_VOLUME_MINIMALE = 0.75;

    /** Ecart de frequence cardiaque, en battements, au-dela duquel on s'interroge. */
    private static final int ECART_FC_SUSPECT = 8;

    /** Tolerance de date pour rapprocher une seance d'une activite. */
    private static final int JOURS_TOLERANCE = 2;

    private final PlannedSessionRepository seances;
    private final ActivityRepository activities;
    private final JournalService journal;

    public ReconciliationService(
            PlannedSessionRepository seances, ActivityRepository activities, JournalService journal) {
        this.seances = seances;
        this.activities = activities;
        this.journal = journal;
    }

    /** Nature d'un ecart, telle que le coach doit la traiter. */
    public enum TypeEcart {
        /** Distance realisee eloignee de la distance prevue, dans un sens ou dans l'autre. */
        ECART_DISTANCE,
        /** Sortie longue ou seance de qualite non faite. */
        SEANCE_CLE_MANQUEE,
        /** Seance ordinaire non faite. */
        SEANCE_MANQUEE,
        /** Frequence cardiaque inhabituelle pour ce type d'effort. */
        FC_SUSPECTE,
        /** Volume de la semaine nettement en dessous de la cible. */
        VOLUME_SOUS_CIBLE,
        /** Douleur ou gene mentionnee au journal. */
        DOULEUR_SIGNALEE,
        /** Activite realisee sans correspondance au plan. */
        SEANCE_HORS_PLAN
    }

    public record Ecart(
            TypeEcart type,
            UUID seanceId,
            UUID activityId,
            LocalDate date,
            String libelle,
            String detail,
            List<String> causesPossibles) {}

    public record SeanceRapprochee(
            UUID seanceId,
            LocalDate datePrevue,
            String type,
            String titre,
            StatutSeance statut,
            BigDecimal distanceCibleKm,
            UUID activityId,
            LocalDate dateRealisee,
            Double distanceRealiseeKm,
            Integer allureSecKm,
            Short fcMoy,
            Double ecartDistancePct,
            boolean conforme) {}

    public record Rapprochement(
            LocalDate debut,
            LocalDate fin,
            Integer numeroSemaine,
            BigDecimal volumeCibleKm,
            double volumeRealiseKm,
            double partDeLaCible,
            List<SeanceRapprochee> seances,
            List<Ecart> ecarts,
            List<String> journalDeLaSemaine) {}

    /**
     * Causes proposees a l'athlete pour un ecart. Les memes que celles que le coach
     * enoncait de vive voix, pour que la question reste ouverte sans etre vague.
     */
    private static final List<String> CAUSES = List.of(
            "Fatigue, jambes lourdes",
            "Douleur ou blessure",
            "Manque de temps, logistique",
            "Meteo",
            "Motivation, mental");

    @Transactional(readOnly = true)
    public Rapprochement rapprocher(Cycle cycle, TrainingWeek semaine) {
        LocalDate debut = semaine.getDateDebut();
        LocalDate fin = debut.plusDays(6);
        return rapprocher(cycle, debut, fin, semaine);
    }

    @Transactional(readOnly = true)
    public Rapprochement rapprocher(Cycle cycle, LocalDate debut, LocalDate fin, TrainingWeek semaine) {
        List<PlannedSession> prevues = seances.entre(cycle.getId(), debut, fin);
        List<Activity> realisees = new ArrayList<>(activities.entre(
                cycle.getAthleteId(), debut.minusDays(JOURS_TOLERANCE), fin.plusDays(JOURS_TOLERANCE)));

        List<SeanceRapprochee> rapprochees = new ArrayList<>();
        List<Ecart> ecarts = new ArrayList<>();
        Set<UUID> consommees = new HashSet<>();

        for (PlannedSession seance : prevues) {
            Activity activite = seance.getActivityId() != null
                    ? realisees.stream()
                            .filter(a -> a.getId().equals(seance.getActivityId()))
                            .findFirst()
                            .orElse(null)
                    : apparier(seance, realisees, consommees);
            if (activite != null) {
                consommees.add(activite.getId());
            }
            SeanceRapprochee rapprochee = construire(seance, activite);
            rapprochees.add(rapprochee);
            ecarts.addAll(qualifier(seance, activite, rapprochee));
        }

        // Une activite sans seance correspondante n'est pas une faute : c'est une information.
        realisees.stream()
                .filter(a -> !consommees.contains(a.getId()))
                .filter(a -> a.getType().estCourseAPied())
                .filter(a -> !a.getDateLocale().isBefore(debut) && !a.getDateLocale().isAfter(fin))
                .forEach(a -> ecarts.add(new Ecart(
                        TypeEcart.SEANCE_HORS_PLAN, null, a.getId(), a.getDateLocale(),
                        "Sortie hors plan le " + a.getDateLocale(),
                        String.format("%.1f km non prevus au plan", a.distanceKm()),
                        List.of())));

        double volumeRealise = realisees.stream()
                .filter(a -> a.getType().estCourseAPied())
                .filter(a -> !a.getDateLocale().isBefore(debut) && !a.getDateLocale().isAfter(fin))
                .mapToDouble(Activity::distanceKm)
                .sum();

        BigDecimal cible = semaine == null ? null : semaine.getVolumeCibleKm();
        double part = cible == null || cible.doubleValue() == 0 ? 1 : volumeRealise / cible.doubleValue();
        if (cible != null && part < PART_VOLUME_MINIMALE) {
            ecarts.add(new Ecart(
                    TypeEcart.VOLUME_SOUS_CIBLE, null, null, debut,
                    "Volume de la semaine en dessous de la cible",
                    String.format("%.1f km realises pour %.0f km vises, soit %.0f %% de la cible",
                            volumeRealise, cible.doubleValue(), part * 100),
                    CAUSES));
        }

        List<JournalEntry> entrees = journal.entre(cycle.getAthleteId(), debut, fin);
        entrees.stream()
                .filter(JournalEntry::isDouleur)
                .forEach(e -> ecarts.add(new Ecart(
                        TypeEcart.DOULEUR_SIGNALEE, null, null, e.getDate(),
                        "Douleur signalee au journal le " + e.getDate(),
                        e.getContenu().length() > 300 ? e.getContenu().substring(0, 300) : e.getContenu(),
                        List.of())));

        return new Rapprochement(
                debut, fin,
                semaine == null ? null : (int) semaine.getNumero(),
                cible,
                Math.round(volumeRealise * 100) / 100.0,
                Math.round(part * 100) / 100.0,
                rapprochees,
                ecarts,
                entrees.stream().map(JournalEntry::getContenu).toList());
    }

    /**
     * Apparie une seance a une activite : meme jour de preference, et lorsque plusieurs
     * activites tombent le meme jour, celle dont la distance approche le mieux la cible.
     */
    private Activity apparier(PlannedSession seance, List<Activity> candidates, Set<UUID> consommees) {
        if (!seance.estCourseAPied()) {
            return null;
        }
        List<Activity> memeJour = candidates.stream()
                .filter(a -> !consommees.contains(a.getId()))
                .filter(a -> a.getType().estCourseAPied())
                .filter(a -> Math.abs(a.getDateLocale().toEpochDay() - seance.getDate().toEpochDay())
                        <= JOURS_TOLERANCE)
                .sorted(Comparator.comparingLong(
                        a -> Math.abs(a.getDateLocale().toEpochDay() - seance.getDate().toEpochDay())))
                .toList();
        if (memeJour.isEmpty()) {
            return null;
        }
        if (seance.getDistanceCibleKm() == null) {
            return memeJour.getFirst();
        }
        double cible = seance.getDistanceCibleKm().doubleValue();
        return memeJour.stream()
                .min(Comparator.comparingDouble(a -> Math.abs(a.distanceKm() - cible)))
                .orElse(memeJour.getFirst());
    }

    private SeanceRapprochee construire(PlannedSession seance, Activity activite) {
        Double ecart = null;
        boolean conforme = false;
        if (activite != null) {
            if (seance.getDistanceCibleKm() != null && seance.getDistanceCibleKm().doubleValue() > 0) {
                double cible = seance.getDistanceCibleKm().doubleValue();
                ecart = Math.round((activite.distanceKm() - cible) / cible * 10000) / 100.0;
                conforme = Math.abs(ecart) <= ECART_DISTANCE_SIGNIFICATIF * 100;
            } else {
                conforme = true;
            }
        }
        return new SeanceRapprochee(
                seance.getId(), seance.getDate(), seance.getType().name(), seance.getTitre(), seance.getStatut(),
                seance.getDistanceCibleKm(),
                activite == null ? null : activite.getId(),
                activite == null ? null : activite.getDateLocale(),
                activite == null ? null : Math.round(activite.distanceKm() * 100) / 100.0,
                activite == null ? null : activite.getAllureMoySecKm(),
                activite == null ? null : activite.getFcMoy(),
                ecart, conforme);
    }

    private List<Ecart> qualifier(PlannedSession seance, Activity activite, SeanceRapprochee rapprochee) {
        List<Ecart> ecarts = new ArrayList<>();

        if (activite == null && seance.estCourseAPied() && seance.getDate().isBefore(LocalDate.now())
                && seance.getStatut() != StatutSeance.ANNULEE && seance.getStatut() != StatutSeance.DEPLACEE) {
            boolean cle = seance.getType().estCle();
            ecarts.add(new Ecart(
                    cle ? TypeEcart.SEANCE_CLE_MANQUEE : TypeEcart.SEANCE_MANQUEE,
                    seance.getId(), null, seance.getDate(),
                    (cle ? "Seance cle non retrouvee : " : "Seance non retrouvee : ") + seance.getTitre(),
                    "Prevue le " + seance.getDate() + ", aucune activite correspondante",
                    CAUSES));
            return ecarts;
        }
        if (activite == null) {
            return ecarts;
        }

        if (rapprochee.ecartDistancePct() != null
                && Math.abs(rapprochee.ecartDistancePct()) > ECART_DISTANCE_SIGNIFICATIF * 100) {
            ecarts.add(new Ecart(
                    TypeEcart.ECART_DISTANCE, seance.getId(), activite.getId(), activite.getDateLocale(),
                    "Distance eloignee du plan : " + seance.getTitre(),
                    String.format("%.1f km realises pour %.1f km prevus (%+.0f %%)",
                            activite.distanceKm(), seance.getDistanceCibleKm().doubleValue(),
                            rapprochee.ecartDistancePct()),
                    CAUSES));
        }

        fcSuspecte(seance, activite).ifPresent(ecarts::add);
        return ecarts;
    }

    /**
     * Frequence cardiaque anormale pour le registre de la seance : haute sur une sortie facile,
     * ou basse sur une seance de qualite — l'un comme l'autre disent que la seance n'a pas ete
     * courue comme elle etait pensee.
     */
    private java.util.Optional<Ecart> fcSuspecte(PlannedSession seance, Activity activite) {
        if (activite.getFcMoy() == null) {
            return java.util.Optional.empty();
        }
        List<Activity> memeType = activities
                .findByAthleteIdOrderByStartedAtDesc(activite.getAthleteId())
                .stream()
                .filter(a -> a.getType() == activite.getType())
                .filter(a -> a.getFcMoy() != null)
                .filter(a -> !a.getId().equals(activite.getId()))
                .limit(20)
                .toList();
        if (memeType.size() < 5) {
            return java.util.Optional.empty();
        }
        int reference = (int) Math.round(memeType.stream().mapToInt(Activity::getFcMoy).average().orElse(0));
        int ecart = activite.getFcMoy() - reference;
        if (Math.abs(ecart) < ECART_FC_SUSPECT) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Ecart(
                TypeEcart.FC_SUSPECTE, seance.getId(), activite.getId(), activite.getDateLocale(),
                "Frequence cardiaque inhabituelle : " + seance.getTitre(),
                String.format("FC moyenne %d contre %d habituellement sur ce type de sortie (%+d)",
                        activite.getFcMoy(), reference, ecart),
                CAUSES));
    }
}
