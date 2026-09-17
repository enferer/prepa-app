package app.prepa.cycle;

import app.prepa.domain.BlocEntrainement;
import app.prepa.domain.LigneDirectrice;
import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeCycle;
import app.prepa.infra.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan glissant des cycles libres.
 *
 * <p>Un cycle libre n'a pas de date de course vers laquelle tout converge : planifier une
 * seance a deux mois n'y aurait aucune valeur, puisque rien ne dit ce que sera la forme
 * d'ici la. Le plan y vit donc a deux regimes — les prochaines semaines sont detaillees
 * seance par seance, les suivantes ne portent que des cibles hebdomadaires. A chaque point,
 * une semaine de plus est detaillee et les cibles restantes sont reajustees.
 *
 * <p>Les cibles elles-memes suivent la progressivite de la methodologie : hausse limitee a
 * dix pour cent d'une semaine sur l'autre, et semaine de decharge toutes les quatre semaines,
 * a volume reduit d'un quart. Ce sont des regles deterministes : autant que le serveur les
 * applique, pour que le coach n'ait plus qu'a decider du contenu des seances.
 */
@Service
public class RollingPlanService {

    /** Nombre de semaines detaillees d'avance. Au-dela, on ne planifie que des cibles. */
    public static final int SEMAINES_DETAILLEES = 4;

    /** Hausse maximale du volume d'une semaine sur l'autre. */
    private static final double PROGRESSION_MAX = 0.10;

    /** Une semaine de decharge revient a ce point toutes les quatre semaines. */
    private static final int PERIODE_DECHARGE = 4;

    private static final double REDUCTION_DECHARGE = 0.25;

    private final CycleRepository cycles;
    private final TrainingWeekRepository semaines;
    private final PlannedSessionRepository seances;

    public RollingPlanService(
            CycleRepository cycles, TrainingWeekRepository semaines, PlannedSessionRepository seances) {
        this.cycles = cycles;
        this.semaines = semaines;
        this.seances = seances;
    }

    /** Une semaine de cibles, telle que le squelette la propose. */
    public record CibleHebdo(
            short numero,
            LocalDate dateDebut,
            BigDecimal volumeCibleKm,
            short nbQualiteCible,
            Integer deniveleCibleM,
            BlocEntrainement bloc,
            boolean detaillee,
            boolean decharge) {}

    /**
     * Squelette de cibles pour un cycle libre, du volume de depart jusqu'a l'horizon.
     *
     * <p>Ce n'est pas un plan : c'est la trame de charge sur laquelle le coach viendra poser
     * des seances. Elle respecte seule la progressivite, ce qui evite qu'une suite de
     * decisions semaine par semaine derive vers une progression intenable.
     */
    public List<CibleHebdo> squelette(Cycle cycle, double volumeDepartKm) {
        if (cycle.getType() != TypeCycle.LIBRE) {
            throw ApiException.invalide("Le plan glissant ne concerne que les cycles libres");
        }
        int nbSemaines = cycle.getHorizonSemaines() == null ? cycle.nbSemaines() : cycle.getHorizonSemaines();
        LigneDirectrice ligne = cycle.getLigneDirectriceType();

        List<CibleHebdo> cibles = new ArrayList<>();
        double volume = volumeDepartKm;
        double volumeReference = volumeDepartKm;

        for (int i = 0; i < nbSemaines; i++) {
            short numero = (short) (i + 1);
            LocalDate debut = cycle.getDateDebut().plusWeeks(i);
            boolean decharge = numero % PERIODE_DECHARGE == 0;

            if (i > 0) {
                if (decharge) {
                    volume = volumeReference * (1 - REDUCTION_DECHARGE);
                } else {
                    // On progresse depuis la derniere semaine pleine, pas depuis la decharge :
                    // sinon chaque decharge ferait repartir la charge en arriere.
                    volumeReference = volumeReference * (1 + PROGRESSION_MAX * facteurProgression(ligne));
                    volume = volumeReference;
                }
            }

            cibles.add(new CibleHebdo(
                    numero,
                    debut,
                    BigDecimal.valueOf(volume).setScale(1, RoundingMode.HALF_UP),
                    (short) (decharge ? Math.max(0, nbQualite(ligne) - 1) : nbQualite(ligne)),
                    denivele(ligne, volume),
                    decharge ? BlocEntrainement.DECHARGE : blocDe(ligne),
                    i < SEMAINES_DETAILLEES,
                    decharge));
        }
        return cibles;
    }

    /**
     * Prolonge le plan d'une semaine detaillee supplementaire.
     *
     * <p>Appele a chaque point hebdomadaire : la fenetre de seances datees avance d'une
     * semaine, et le coach n'a qu'a remplir celle qui vient de s'ouvrir.
     */
    @Transactional
    public Optional<TrainingWeek> ouvrirSemaineSuivante(UUID cycleId) {
        Cycle cycle = cycles.findById(cycleId).orElseThrow(() -> ApiException.notFound("Cycle"));
        List<TrainingWeek> toutes = semaines.findByCycleIdOrderByNumeroAsc(cycleId);

        Optional<TrainingWeek> aOuvrir = toutes.stream()
                .filter(s -> !s.isDetaillee())
                .findFirst();
        aOuvrir.ifPresent(semaine -> {
            semaine.setDetaillee(true);
            semaines.save(semaine);
        });
        return aOuvrir;
    }

    /**
     * Repousse l'horizon d'un cycle libre et complete la trame jusqu'a la nouvelle echeance.
     * Les semaines deja posees ne bougent pas : on ajoute a la suite.
     */
    @Transactional
    public List<TrainingWeek> prolonger(UUID cycleId, int semainesSupplementaires) {
        Cycle cycle = cycles.findById(cycleId).orElseThrow(() -> ApiException.notFound("Cycle"));
        if (cycle.getType() != TypeCycle.LIBRE) {
            throw ApiException.invalide("Seul un cycle libre se prolonge ; une préparation a une date de course");
        }
        List<TrainingWeek> existantes = semaines.findByCycleIdOrderByNumeroAsc(cycleId);
        if (existantes.isEmpty()) {
            throw ApiException.invalide("Ce cycle n'a pas encore de plan à prolonger");
        }

        TrainingWeek derniere = existantes.getLast();
        double volume = derniere.getVolumeCibleKm().doubleValue();
        List<TrainingWeek> ajoutees = new ArrayList<>();

        for (int i = 1; i <= semainesSupplementaires; i++) {
            short numero = (short) (derniere.getNumero() + i);
            boolean decharge = numero % PERIODE_DECHARGE == 0;
            double volumeSemaine = decharge ? volume * (1 - REDUCTION_DECHARGE) : volume;

            TrainingWeek semaine = new TrainingWeek(
                    UUID.randomUUID(),
                    cycleId,
                    numero,
                    derniere.getDateDebut().plusWeeks(i),
                    BigDecimal.valueOf(volumeSemaine).setScale(1, RoundingMode.HALF_UP));
            semaine.setBloc(decharge ? BlocEntrainement.DECHARGE : BlocEntrainement.LIBRE);
            semaine.setNbQualiteCible(derniere.getNbQualiteCible());
            semaine.setDetaillee(false);
            ajoutees.add(semaines.save(semaine));
        }

        short nouvelHorizon = (short) (existantes.size() + semainesSupplementaires);
        cycle.setHorizonSemaines(nouvelHorizon);
        cycle.setDateFin(cycle.getDateDebut().plusWeeks(nouvelHorizon));
        cycles.save(cycle);
        return ajoutees;
    }

    /**
     * Bilan de fin de cycle : ce qui a ete tenu, ce qui ne l'a pas ete.
     * Sert de point de depart au bilan redige par le coach a la cloture.
     */
    @Transactional(readOnly = true)
    public BilanCycle bilan(UUID cycleId) {
        Cycle cycle = cycles.findById(cycleId).orElseThrow(() -> ApiException.notFound("Cycle"));
        List<TrainingWeek> toutes = semaines.findByCycleIdOrderByNumeroAsc(cycleId);
        List<PlannedSession> planifiees = seances.findByCycleIdOrderByDateAscOrdreAsc(cycleId);

        // Le renforcement et le repos sortent du calcul d'assiduite : la montre ne les
        // enregistre pas, si bien qu'ils paraissent manques alors qu'ils ont souvent ete faits.
        // Les compter ferait chuter le chiffre pour une raison etrangere a l'entrainement.
        List<PlannedSession> retenues = planifiees.stream()
                .filter(PlannedSession::estCourseAPied)
                .toList();
        long tenues = retenues.stream().filter(s -> s.getStatut().aEuLieu()).count();
        long manquees = retenues.stream()
                .filter(s -> s.getStatut() == StatutSeance.NON_REALISEE)
                .count();
        long tranchees = tenues + manquees;

        double volumeCible = toutes.stream()
                .mapToDouble(s -> s.getVolumeCibleKm().doubleValue())
                .sum();

        return new BilanCycle(
                cycle.getNom(),
                cycle.getType().name(),
                cycle.getDateDebut(),
                cycle.getDateFin(),
                toutes.size(),
                planifiees.size(),
                (int) tenues,
                (int) manquees,
                // L'assiduite ne se mesure que sur ce qui est tranche : une seance a venir
                // n'est ni tenue ni manquee.
                tranchees == 0 ? null : (int) Math.round(tenues * 100.0 / tranchees),
                Math.round(volumeCible * 10) / 10.0);
    }

    public record BilanCycle(
            String nom,
            String type,
            LocalDate dateDebut,
            LocalDate dateFin,
            int nbSemaines,
            int nbSeances,
            int seancesTenues,
            int seancesManquees,
            Integer assiduitePct,
            double volumeCibleTotalKm) {}

    /**
     * Toutes les lignes directrices ne se pretent pas a la meme montee en charge : on ne
     * progresse pas en volume quand on cherche la puissance aerobie, et une reprise se fait
     * a plat.
     */
    private static double facteurProgression(LigneDirectrice ligne) {
        if (ligne == null) {
            return 1;
        }
        return switch (ligne) {
            case MAINTIEN_CHARGE -> 0;
            case REPRISE_POST_COURSE, RETOUR_BLESSURE -> 0.5;
            case VO2MAX, VITESSE_COURTE -> 0.3;
            case ENDURANCE_FONDAMENTALE, TRAIL_DENIVELE -> 1;
            case AUTRE -> 0.7;
        };
    }

    /** Nombre de seances de qualite hebdomadaires que suppose la ligne directrice. */
    private static short nbQualite(LigneDirectrice ligne) {
        if (ligne == null) {
            return 1;
        }
        return switch (ligne) {
            case VO2MAX, VITESSE_COURTE -> 2;
            case ENDURANCE_FONDAMENTALE, REPRISE_POST_COURSE, RETOUR_BLESSURE -> 0;
            default -> 1;
        };
    }

    /** Un cycle oriente trail se pilote au denivele autant qu'au kilometrage. */
    private static Integer denivele(LigneDirectrice ligne, double volumeKm) {
        return ligne == LigneDirectrice.TRAIL_DENIVELE ? (int) Math.round(volumeKm * 25) : null;
    }

    private static BlocEntrainement blocDe(LigneDirectrice ligne) {
        return ligne == LigneDirectrice.REPRISE_POST_COURSE || ligne == LigneDirectrice.RETOUR_BLESSURE
                ? BlocEntrainement.REPRISE
                : BlocEntrainement.LIBRE;
    }
}
