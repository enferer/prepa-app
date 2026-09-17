package app.prepa.analysis;

import app.prepa.activity.Activity;
import app.prepa.activity.ActivityLap;
import app.prepa.activity.ActivityLap;
import app.prepa.activity.ActivityRepository;
import app.prepa.athlete.AthleteProfileService;
import app.prepa.cycle.Cycle;
import app.prepa.cycle.CycleService;
import app.prepa.cycle.PlannedSession;
import app.prepa.domain.IntensiteTour;
import app.prepa.domain.TypeSeance;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synthese d'entrainement : volume, allures reelles, efforts notables, tendances.
 *
 * <p>Porte la lecture macro que le coach faisait jusqu'ici par un script local. Trois points
 * s'ecartent volontairement de l'original :
 *
 * <ul>
 *   <li>le tapis de course compte dans le volume — il en etait exclu par un libelle qui ne
 *       correspondait pas a celui qu'ecrivait le synchroniseur ;
 *   <li>le seuil d'endurance fondamentale est relatif a la frequence cardiaque maximale
 *       plutot que fixe a 145 battements, valeur qui ne veut rien dire pour un athlete dont
 *       la FC max s'ecarte de la moyenne ;
 *   <li>les meilleurs temps se calculent par fenetre glissante sur les tours, au lieu d'etre
 *       extrapoles de l'allure moyenne de la sortie entiere.
 * </ul>
 */
@Service
public class AnalysisService {

    /** Distances sur lesquelles on cherche un meilleur effort. */
    private static final List<Integer> DISTANCES_RECORD = List.of(1000, 5000, 10000, 21097, 42195);

    /** Une sortie doit atteindre cette distance pour temoigner d'une allure d'endurance. */
    private static final double DISTANCE_MIN_EF_KM = 5;

    /** Au-dela, le denivele fausse la lecture de l'allure. */
    private static final int DENIVELE_MAX_PLAT_M = 120;

    private static final double DISTANCE_MIN_EFFORT_KM = 8;

    private final ActivityRepository activities;
    private final AthleteProfileService profils;
    private final CycleService cycles;

    public AnalysisService(
            ActivityRepository activities, AthleteProfileService profils, CycleService cycles) {
        this.activities = activities;
        this.profils = profils;
        this.cycles = cycles;
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.Synthese analyser(UUID athleteId, Integer jours, LocalDate depuis) {
        List<Activity> courses = coursesAPied(athleteId);
        if (courses.isEmpty()) {
            return vide();
        }

        LocalDate fin = courses.getLast().getDateLocale();
        LocalDate seuil;
        String libelle;
        if (depuis != null) {
            seuil = depuis;
            libelle = "depuis le " + depuis;
        } else {
            int fenetre = jours == null ? 90 : jours;
            seuil = fin.minusDays(fenetre);
            libelle = fenetre + " derniers jours";
        }
        List<Activity> recentes = courses.stream()
                .filter(a -> !a.getDateLocale().isBefore(seuil))
                .toList();

        int seuilFc = profils.profil(athleteId).seuilFcEnduranceFondamentale();
        boolean seuilRelatif = profils.profil(athleteId).getFcMax() != null;

        return new AnalysisDtos.Synthese(
                new AnalysisDtos.Fenetre(
                        courses.getFirst().getDateLocale(), fin, courses.size(), libelle),
                volumeHebdomadaire(courses),
                resumeVolume(recentes),
                allureEnduranceFondamentale(recentes, seuilFc, seuilRelatif),
                effortsRapides(recentes),
                plusLonguesSorties(recentes),
                effortNotable(courses.stream()
                        .max(Comparator.comparingInt(a -> a.getDistanceM() == null ? 0 : a.getDistanceM()))
                        .orElseThrow()),
                records(courses),
                tendanceFc(courses),
                comparaisonCycle(athleteId, courses),
                alluresParType(athleteId, recentes),
                repartitionIntensite(athleteId, recentes, seuilFc));
    }

    /** Volume, semaine ISO par semaine ISO, sans trou dans la serie. */
    private List<AnalysisDtos.SemaineVolume> volumeHebdomadaire(List<Activity> courses) {
        Map<LocalDate, double[]> parSemaine = new LinkedHashMap<>();
        for (Activity a : courses) {
            LocalDate lundi = a.getDateLocale().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            double[] cumul = parSemaine.computeIfAbsent(lundi, k -> new double[3]);
            cumul[0] += a.distanceKm();
            cumul[1] += 1;
            cumul[2] += a.getDenivelePosM() == null ? 0 : a.getDenivelePosM();
        }
        if (parSemaine.isEmpty()) {
            return List.of();
        }

        // Une semaine sans sortie est une information : on la laisse a zero plutot que de
        // l'omettre, sinon une coupure se lit comme une continuite.
        LocalDate courante = parSemaine.keySet().iterator().next();
        LocalDate derniere = courses.getLast().getDateLocale().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<AnalysisDtos.SemaineVolume> semaines = new ArrayList<>();
        while (!courante.isAfter(derniere)) {
            double[] cumul = parSemaine.getOrDefault(courante, new double[3]);
            semaines.add(new AnalysisDtos.SemaineVolume(
                    courante,
                    courante.get(IsoFields.WEEK_BASED_YEAR),
                    courante.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                    arrondi(cumul[0]),
                    (int) cumul[1],
                    (int) cumul[2]));
            courante = courante.plusWeeks(1);
        }
        return semaines;
    }

    private AnalysisDtos.VolumeResume resumeVolume(List<Activity> courses) {
        if (courses.isEmpty()) {
            return new AnalysisDtos.VolumeResume(0, 0, 0, 0, 0, 0, 0);
        }
        List<AnalysisDtos.SemaineVolume> semaines = volumeHebdomadaire(courses).stream()
                .filter(s -> s.nbCourses() > 0)
                .toList();
        double kmTotal = courses.stream().mapToDouble(Activity::distanceKm).sum();
        double[] vols = semaines.stream().mapToDouble(AnalysisDtos.SemaineVolume::km).toArray();

        return new AnalysisDtos.VolumeResume(
                courses.size(),
                arrondi(kmTotal),
                semaines.size(),
                arrondi(kmTotal / Math.max(1, semaines.size())),
                arrondi(java.util.Arrays.stream(vols).min().orElse(0)),
                arrondi(java.util.Arrays.stream(vols).max().orElse(0)),
                arrondi((double) courses.size() / Math.max(1, semaines.size())));
    }

    private AnalysisDtos.AllureEf allureEnduranceFondamentale(
            List<Activity> courses, int seuilFc, boolean relatif) {
        List<Activity> faciles = courses.stream()
                .filter(a -> a.getAllureMoySecKm() != null)
                .filter(a -> a.distanceKm() >= DISTANCE_MIN_EF_KM)
                .filter(a -> a.getFcMoy() != null && a.getFcMoy() <= seuilFc)
                .filter(a -> (a.getDenivelePosM() == null ? 0 : a.getDenivelePosM()) < DENIVELE_MAX_PLAT_M)
                .toList();
        Integer moyenne = faciles.isEmpty()
                ? null
                : (int) Math.round(faciles.stream().mapToInt(Activity::getAllureMoySecKm).average().orElse(0));
        return new AnalysisDtos.AllureEf(moyenne, faciles.size(), seuilFc, relatif);
    }

    private List<AnalysisDtos.EffortNotable> effortsRapides(List<Activity> courses) {
        return courses.stream()
                .filter(a -> a.getAllureMoySecKm() != null)
                .filter(a -> a.distanceKm() >= DISTANCE_MIN_EFFORT_KM)
                .filter(a -> (a.getDenivelePosM() == null ? 0 : a.getDenivelePosM()) < DENIVELE_MAX_PLAT_M)
                .sorted(Comparator.comparingInt(Activity::getAllureMoySecKm))
                .limit(6)
                .map(this::effortNotable)
                .toList();
    }

    private List<AnalysisDtos.EffortNotable> plusLonguesSorties(List<Activity> courses) {
        return courses.stream()
                .sorted(Comparator.comparingInt((Activity a) -> a.getDistanceM() == null ? 0 : a.getDistanceM())
                        .reversed())
                .limit(5)
                .map(this::effortNotable)
                .toList();
    }

    private AnalysisDtos.EffortNotable effortNotable(Activity a) {
        return new AnalysisDtos.EffortNotable(
                a.getId(), a.getDateLocale(), a.getType(), a.getTitre(), arrondi(a.distanceKm()),
                a.getAllureMoySecKm(), a.getFcMoy(), a.getDenivelePosM());
    }

    /**
     * Meilleurs temps par fenetre glissante sur les tours.
     *
     * <p>Une sortie de quinze kilometres contient peut-etre un dix kilometres rapide, mais son
     * allure moyenne le noie. Parcourir les tours en fenetre glissante retrouve le meilleur
     * segment reellement couru — la ou l'ancienne estimation multipliait simplement l'allure
     * moyenne par la distance, et ne pouvait donc jamais reveler mieux que la moyenne.
     */
    private List<AnalysisDtos.RecordEstime> records(List<Activity> courses) {
        List<AnalysisDtos.RecordEstime> records = new ArrayList<>();
        for (int distance : DISTANCES_RECORD) {
            AnalysisDtos.RecordEstime meilleur = null;
            for (Activity a : courses) {
                AnalysisDtos.RecordEstime candidat = meilleurSegment(a, distance);
                if (candidat != null && (meilleur == null || candidat.tempsSec() < meilleur.tempsSec())) {
                    meilleur = candidat;
                }
            }
            if (meilleur != null) {
                records.add(meilleur);
            }
        }
        return records;
    }

    private AnalysisDtos.RecordEstime meilleurSegment(Activity a, int distanceM) {
        if (a.getDistanceM() == null || a.getDistanceM() < distanceM) {
            return null;
        }
        List<ActivityLap> tours = a.getTours();
        if (tours.size() > 1) {
            Integer temps = fenetreGlissante(tours, distanceM);
            if (temps != null) {
                return new AnalysisDtos.RecordEstime(
                        distanceM, temps, allure(temps, distanceM), a.getDateLocale(), a.getId(), true);
            }
        }
        if (a.getAllureMoySecKm() == null) {
            return null;
        }
        int estime = (int) Math.round(a.getAllureMoySecKm() * (distanceM / 1000.0));
        return new AnalysisDtos.RecordEstime(
                distanceM, estime, a.getAllureMoySecKm(), a.getDateLocale(), a.getId(), false);
    }

    /**
     * Meilleur temps sur une distance, en faisant glisser une fenetre sur les tours.
     * Le dernier tour de la fenetre est pris au prorata quand il depasse la distance visee.
     */
    private Integer fenetreGlissante(List<ActivityLap> tours, int distanceM) {
        Integer meilleur = null;
        for (int debut = 0; debut < tours.size(); debut++) {
            int cumulDistance = 0;
            int cumulTemps = 0;
            for (int i = debut; i < tours.size(); i++) {
                ActivityLap tour = tours.get(i);
                int distanceTour = tour.getDistanceM() == null ? 0 : tour.getDistanceM();
                if (distanceTour <= 0) {
                    continue;
                }
                if (cumulDistance + distanceTour >= distanceM) {
                    int manquant = distanceM - cumulDistance;
                    int temps = cumulTemps + Math.round(tour.getDureeSec() * (manquant / (float) distanceTour));
                    if (meilleur == null || temps < meilleur) {
                        meilleur = temps;
                    }
                    break;
                }
                cumulDistance += distanceTour;
                cumulTemps += tour.getDureeSec();
            }
        }
        return meilleur;
    }

    /**
     * Derive de la frequence cardiaque entre les quatre dernieres semaines et les quatre
     * precedentes. Une FC qui monte a charge egale est un signe de fatigue.
     */
    private AnalysisDtos.TendanceFc tendanceFc(List<Activity> courses) {
        LocalDate fin = courses.getLast().getDateLocale();
        Integer recente = fcMoyenne(courses, fin.minusWeeks(4), fin);
        Integer precedente = fcMoyenne(courses, fin.minusWeeks(8), fin.minusWeeks(4));
        if (recente == null || precedente == null) {
            return new AnalysisDtos.TendanceFc(recente, precedente, null, "Historique insuffisant");
        }
        int ecart = recente - precedente;
        String lecture = ecart >= 4
                ? "Fréquence cardiaque en hausse à charge comparable : signe de fatigue à croiser avec le journal"
                : ecart <= -4 ? "Fréquence cardiaque en baisse : signe d'une meilleure fraîcheur"
                        : "Fréquence cardiaque stable";
        return new AnalysisDtos.TendanceFc(recente, precedente, ecart, lecture);
    }

    private Integer fcMoyenne(List<Activity> courses, LocalDate debut, LocalDate fin) {
        List<Activity> fenetre = courses.stream()
                .filter(a -> !a.getDateLocale().isBefore(debut) && !a.getDateLocale().isAfter(fin))
                .filter(a -> a.getFcMoy() != null)
                .toList();
        return fenetre.isEmpty()
                ? null
                : (int) Math.round(fenetre.stream().mapToInt(Activity::getFcMoy).average().orElse(0));
    }

    /** Ce que le cycle en cours a change par rapport a la periode qui l'a precede. */
    private AnalysisDtos.Comparaison comparaisonCycle(UUID athleteId, List<Activity> courses) {
        Optional<Cycle> cycle = cycles.actif(athleteId);
        if (cycle.isEmpty()) {
            return null;
        }
        LocalDate debut = cycle.get().getDateDebut();
        List<Activity> avant = courses.stream().filter(a -> a.getDateLocale().isBefore(debut)).toList();
        List<Activity> pendant = courses.stream().filter(a -> !a.getDateLocale().isBefore(debut)).toList();
        if (avant.isEmpty() || pendant.isEmpty()) {
            return null;
        }

        AnalysisDtos.Periode periodeAvant = periode("Avant le cycle", avant);
        AnalysisDtos.Periode periodePendant = periode(cycle.get().getNom(), pendant);

        List<AnalysisDtos.Delta> deltas = new ArrayList<>();
        deltas.add(delta("Volume hebdomadaire",
                periodeAvant.volume().kmParSemaineMoyen(), periodePendant.volume().kmParSemaineMoyen(), true));
        deltas.add(delta("Séances par semaine",
                periodeAvant.volume().seancesParSemaine(), periodePendant.volume().seancesParSemaine(), true));
        if (periodeAvant.allureMoySecKm() != null && periodePendant.allureMoySecKm() != null) {
            // Une allure qui baisse est une amelioration : le sens de la comparaison s'inverse.
            deltas.add(delta("Allure moyenne",
                    periodeAvant.allureMoySecKm().doubleValue(), periodePendant.allureMoySecKm().doubleValue(), false));
        }
        return new AnalysisDtos.Comparaison(periodeAvant, periodePendant, deltas);
    }

    private AnalysisDtos.Periode periode(String libelle, List<Activity> courses) {
        Integer allure = courses.stream()
                .filter(a -> a.getAllureMoySecKm() != null)
                .mapToInt(Activity::getAllureMoySecKm)
                .average()
                .stream()
                .mapToObj(d -> (int) Math.round(d))
                .findFirst()
                .orElse(null);
        Integer fc = courses.stream()
                .filter(a -> a.getFcMoy() != null)
                .mapToInt(Activity::getFcMoy)
                .average()
                .stream()
                .mapToObj(d -> (int) Math.round(d))
                .findFirst()
                .orElse(null);
        return new AnalysisDtos.Periode(
                libelle, courses.getFirst().getDateLocale(), courses.getLast().getDateLocale(),
                resumeVolume(courses), allure, fc);
    }

    private AnalysisDtos.Delta delta(String mesure, double avant, double pendant, boolean plusEstMieux) {
        double variation = avant == 0 ? 0 : (pendant - avant) / avant * 100;
        boolean amelioration = plusEstMieux ? pendant > avant : pendant < avant;
        return new AnalysisDtos.Delta(mesure, arrondi(avant), arrondi(pendant), arrondi(variation), amelioration);
    }

    /**
     * Allure reellement tenue par type de seance, face a la cible du cycle.
     *
     * <p>Sur une seance a intervalles, la moyenne de la sortie melange l'echauffement, les
     * recuperations et le retour au calme : elle ne se compare a aucune cible. On retient
     * donc l'allure des seuls blocs d'effort, ponderee par leur duree.
     */
    private List<AnalysisDtos.AllureParType> alluresParType(UUID athleteId, List<Activity> courses) {
        Optional<Cycle> cycle = cycles.actif(athleteId);
        if (cycle.isEmpty()) {
            return List.of();
        }
        Map<UUID, Activity> parActivite = courses.stream()
                .collect(java.util.stream.Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));

        Map<TypeSeance, List<Integer>> alluresRetenues = new java.util.EnumMap<>(TypeSeance.class);
        Map<TypeSeance, Boolean> surBlocs = new java.util.EnumMap<>(TypeSeance.class);

        for (PlannedSession seance : cycles.seancesDe(cycle.get().getId())) {
            if (seance.getActivityId() == null) {
                continue;
            }
            Activity activite = parActivite.get(seance.getActivityId());
            if (activite == null) {
                continue;
            }
            Integer allure = seance.getType().estAllureContinue()
                    ? activite.getAllureMoySecKm()
                    : allureDesBlocsDEffort(activite);
            if (allure == null) {
                continue;
            }
            alluresRetenues.computeIfAbsent(seance.getType(), t -> new ArrayList<>()).add(allure);
            surBlocs.putIfAbsent(seance.getType(), !seance.getType().estAllureContinue());
        }

        List<AnalysisDtos.AllureParType> resultat = new ArrayList<>();
        for (var entree : alluresRetenues.entrySet()) {
            TypeSeance type = entree.getKey();
            int reelle = (int) Math.round(entree.getValue().stream().mapToInt(Integer::intValue).average().orElse(0));
            Integer cible = allureCible(cycle.get(), type);
            resultat.add(new AnalysisDtos.AllureParType(
                    type.name(),
                    libelle(type),
                    entree.getValue().size(),
                    reelle,
                    cible,
                    cible == null ? null : reelle - cible,
                    Boolean.TRUE.equals(surBlocs.get(type))));
        }
        resultat.sort(java.util.Comparator.comparing(AnalysisDtos.AllureParType::type));
        return resultat;
    }

    /**
     * Allure des seuls blocs d'effort, ponderee par leur duree.
     *
     * <p>Seuls les tours marques {@code INTERVAL} comptent, et a defaut les tours actifs d'une
     * seance qui melange plusieurs intensites. Sur une sortie enregistree en tours
     * automatiques, tous les tours sont actifs : les retenir reviendrait a reprendre l'allure
     * moyenne de la sortie et a la presenter comme une allure de VMA — un chiffre faux vaut
     * moins que pas de chiffre du tout.
     */
    private Integer allureDesBlocsDEffort(Activity activite) {
        List<ActivityLap> tours = activite.getTours();
        boolean structuree = tours.stream()
                        .map(t -> t.getIntensite() == null ? IntensiteTour.UNKNOWN : t.getIntensite())
                        .distinct()
                        .count()
                > 1;

        // Tous les tours de meme intensite : le decoupage est kilometrique, pas structurel.
        // Une seance de huit fois quarante-cinq secondes enregistree en tours automatiques
        // donne huit kilometres tous marques « effort » — en tirer une allure de VMA
        // reviendrait a annoncer 6:34 la ou l'athlete a couru a 4:10 sur ses fractions.
        if (!structuree) {
            return null;
        }
        Integer surIntervalles = allurePonderee(tours, java.util.Set.of(IntensiteTour.INTERVAL));
        return surIntervalles != null ? surIntervalles : allurePonderee(tours, java.util.Set.of(IntensiteTour.ACTIVE));
    }

    private Integer allurePonderee(List<ActivityLap> tours, java.util.Set<IntensiteTour> retenues) {
        long distance = 0;
        long duree = 0;
        for (ActivityLap tour : tours) {
            if (retenues.contains(tour.getIntensite()) && tour.getDistanceM() != null && tour.getDistanceM() > 0) {
                distance += tour.getDistanceM();
                duree += tour.getDureeSec();
            }
        }
        return distance > 0 ? (int) Math.round(duree / (distance / 1000.0)) : null;
    }

    /** L'allure visee pour ce type, telle que le coach l'a posee sur le cycle. */
    private Integer allureCible(Cycle cycle, TypeSeance type) {
        Object valeur = cycle.getAlluresCibles().get(type.name());
        if (valeur == null) {
            valeur = cycle.getAlluresCibles().get(libelleCourt(type));
        }
        if (valeur instanceof Map<?, ?> zone && zone.get("secKm") instanceof Number secKm) {
            return secKm.intValue();
        }
        return null;
    }

    /**
     * Repartition du volume entre endurance et intensite.
     *
     * <p>Le classement suit d'abord le plan — une seance de seuil est de l'intensite, quelle
     * que soit la frequence cardiaque relevee. Faute de seance rapprochee, on retombe sur la
     * frequence cardiaque, qui reste le meilleur temoin de l'effort fourni.
     */
    private AnalysisDtos.RepartitionIntensite repartitionIntensite(
            UUID athleteId, List<Activity> courses, int seuilFc) {
        Map<UUID, TypeSeance> typeParActivite = new java.util.HashMap<>();
        cycles.actif(athleteId).ifPresent(cycle -> cycles.seancesDe(cycle.getId()).stream()
                .filter(s -> s.getActivityId() != null)
                .forEach(s -> typeParActivite.put(s.getActivityId(), s.getType())));

        double facile = 0;
        double intensite = 0;
        for (Activity activite : courses) {
            TypeSeance type = typeParActivite.get(activite.getId());
            boolean dur = type != null
                    ? type.estQualite()
                    : activite.getFcMoy() != null && activite.getFcMoy() > seuilFc;
            if (dur) {
                intensite += activite.distanceKm();
            } else {
                facile += activite.distanceKm();
            }
        }

        double total = facile + intensite;
        if (total == 0) {
            return null;
        }
        int partFacile = (int) Math.round(facile / total * 100);
        return new AnalysisDtos.RepartitionIntensite(
                arrondi(facile), arrondi(intensite), partFacile, 100 - partFacile, lectureRepartition(partFacile));
    }

    /** Ce que la repartition dit de l'entrainement, en une phrase. */
    private static String lectureRepartition(int partFacile) {
        if (partFacile >= 75 && partFacile <= 88) {
            return "Ton équilibre est bon : l'essentiel du volume en facile, ce qu'il faut d'intensité.";
        }
        if (partFacile > 88) {
            return "Très peu d'intensité. Le volume facile construit le socle, mais sans seuil ni VMA "
                    + "la progression finit par plafonner.";
        }
        return "Beaucoup d'intensité pour le volume couru. C'est le défaut le plus répandu : courir son "
                + "facile trop vite laisse moins de fraîcheur pour les séances qui comptent.";
    }

    private static String libelle(TypeSeance type) {
        return switch (type) {
            case EF -> "Endurance";
            case SL -> "Sortie longue";
            case SEUIL -> "Seuil";
            case VMA -> "VMA";
            case AM -> "Allure marathon";
            case COTES -> "Côtes";
            case COURSE -> "Course";
            case CROSS -> "Cross-training";
            case RENFO -> "Renforcement";
            case REPOS -> "Repos";
        };
    }

    /** Les cles d'allures cibles suivent les abreviations du coach. */
    private static String libelleCourt(TypeSeance type) {
        return switch (type) {
            case SEUIL -> "Seuil";
            case COTES -> "Côtes";
            default -> type.name();
        };
    }

    /** Les activites de course a pied d'un athlete, de la plus ancienne a la plus recente. */
    private List<Activity> coursesAPied(UUID athleteId) {
        return activities.findByAthleteIdOrderByStartedAtDesc(athleteId).stream()
                .filter(a -> a.getType().estCourseAPied())
                .filter(a -> a.getDistanceM() != null && a.getDistanceM() > 0)
                .sorted(Comparator.comparing(Activity::getDateLocale))
                .toList();
    }

    private static AnalysisDtos.Synthese vide() {
        return new AnalysisDtos.Synthese(
                null, List.of(), new AnalysisDtos.VolumeResume(0, 0, 0, 0, 0, 0, 0),
                new AnalysisDtos.AllureEf(null, 0, 145, false), List.of(), List.of(), null, List.of(), null, null,
                List.of(), null);
    }

    private static Integer allure(int tempsSec, int distanceM) {
        return Math.round(tempsSec / (distanceM / 1000f));
    }

    private static double arrondi(double valeur) {
        return Math.round(valeur * 100) / 100.0;
    }
}
