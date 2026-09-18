package app.prepa.analysis;

import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteProfileService;
import app.prepa.athlete.AthleteService;
import app.prepa.athlete.PersonalRecord;
import app.prepa.coach.CoachNoteService;
import app.prepa.coach.WeeklyReport;
import app.prepa.coach.WeeklyReportService;
import app.prepa.cycle.Cycle;
import app.prepa.cycle.CycleService;
import app.prepa.cycle.PlannedSession;
import app.prepa.cycle.TrainingWeek;
import app.prepa.journal.JournalService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le paquet de depart d'un point avec le coach.
 *
 * <p>Tout l'enjeu est la <em>borne</em>. Le coach n'a pas besoin de tout l'historique pour
 * juger une semaine : il lui faut qui est l'athlete, ou il en est, ce qui le limite en ce
 * moment, et les quelques decisions passees qui valent encore. Servir davantage ne rend pas
 * le jugement meilleur — cela le noie.
 *
 * <p>Chaque liste est donc plafonnee, et la memoire du coach filtree par portee plutot que
 * servie en entier.
 */
@Service
public class CoachContextService {

    /** Journal : de quoi couvrir la semaine ecoulee et un peu de contexte avant elle. */
    private static final int ENTREES_JOURNAL = 5;

    private static final int RECORDS_RETENUS = 6;

    private final AthleteService athletes;
    private final AthleteProfileService profils;
    private final CycleService cycles;
    private final JournalService journal;
    private final CoachNoteService notes;
    private final WeeklyReportService rapports;

    public CoachContextService(
            AthleteService athletes,
            AthleteProfileService profils,
            CycleService cycles,
            JournalService journal,
            CoachNoteService notes,
            WeeklyReportService rapports) {
        this.athletes = athletes;
        this.profils = profils;
        this.cycles = cycles;
        this.journal = journal;
        this.notes = notes;
        this.rapports = rapports;
    }

    public record CoachContext(
            AthleteResume athlete,
            ProfilResume profil,
            List<RecordResume> records,
            List<BlessureResume> blessuresEnCours,
            List<ContrainteResume> contraintesEnCours,
            List<SignatureResume> seancesSignature,
            CycleResume cycle,
            SemaineResume semaineCourante,
            List<NoteResume> memoire,
            List<JournalResume> journalRecent,
            DernierBilan dernierBilan,
            /** Signal au coach quand sa memoire durable s'alourdit au point de nuire. */
            String avertissement) {}

    public record AthleteResume(UUID id, String nom, String timezone) {}

    public record ProfilResume(
            Short fcMax,
            BigDecimal vmaKmh,
            BigDecimal volumeHabituelKm,
            List<String> joursDisponibles,
            boolean renfoActif,
            String renfoFocus) {}

    public record RecordResume(int distanceM, int tempsSec, LocalDate date) {}

    public record BlessureResume(String zone, String statut, Short palier, String consignes) {}

    public record ContrainteResume(String type, LocalDate debut, LocalDate fin, String detail) {}

    public record SignatureResume(String nom, String type, String frequenceSouhaitee, boolean actif) {}

    public record CycleResume(
            UUID id,
            String nom,
            String type,
            LocalDate dateDebut,
            LocalDate dateFin,
            String courseNom,
            LocalDate courseDate,
            Integer chronoViseSec,
            Long joursAvantCourse,
            String ligneDirectrice,
            String ligneDirectriceType,
            Short horizonSemaines,
            java.util.Map<String, Object> alluresCibles,
            int semainesDetaillees,
            int semainesTotal) {}

    /**
     * La semaine en cours.
     *
     * <p>{@code volumeCibleKm} est ce que la semaine vise, {@code volumePlanifieKm} ce que ses
     * seances totalisent. Un ecart entre les deux n'est pas une anomalie : c'est la trace d'une
     * adaptation faite seance par seance, que le coach voit ici et decide ou non d'enteriner.
     */
    public record SemaineResume(
            UUID id,
            Integer numero,
            LocalDate dateDebut,
            String bloc,
            BigDecimal volumeCibleKm,
            BigDecimal volumePlanifieKm,
            boolean detaillee,
            String note,
            List<SeanceResume> seances) {}

    public record SeanceResume(
            UUID id, LocalDate date, String type, String titre, String statut, BigDecimal distanceCibleKm,
            String alluresTexte) {}

    public record NoteResume(UUID id, LocalDate date, String portee, String categorie, String titre, String contenu) {}

    public record JournalResume(LocalDate date, String contenu, Short humeur, Short fatigue, boolean douleur) {}

    public record DernierBilan(LocalDate dateDebut, String bilan, String consignes) {}

    @Transactional(readOnly = true)
    public CoachContext construire(UUID athleteId) {
        Athlete athlete = athletes.parId(athleteId);
        Cycle cycle = cycles.actif(athleteId).orElse(null);

        return new CoachContext(
                new AthleteResume(athlete.getId(), athlete.getDisplayName(), athlete.getTimezone()),
                profilResume(athleteId),
                records(athleteId),
                blessures(athleteId),
                contraintes(athleteId),
                signatures(athleteId),
                cycle == null ? null : cycleResume(cycle),
                cycle == null ? null : semaineCourante(cycle),
                memoire(athleteId, cycle),
                journalRecent(athleteId),
                dernierBilan(athleteId),
                notes.alerteVolumetrie(athleteId));
    }

    private ProfilResume profilResume(UUID athleteId) {
        var profil = profils.profil(athleteId);
        return new ProfilResume(
                profil.getFcMax(), profil.getVmaKmh(), profil.getVolumeHabituelKm(),
                profil.getJoursDisponibles() == null ? List.of() : List.of(profil.getJoursDisponibles()),
                profil.isRenfoActif(), profil.getRenfoFocus());
    }

    /** Le meilleur temps par distance, pas tout l'historique des records. */
    private List<RecordResume> records(UUID athleteId) {
        return profils.records(athleteId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PersonalRecord::getDistanceM,
                        r -> r,
                        (a, b) -> a.getTempsSec() <= b.getTempsSec() ? a : b))
                .values()
                .stream()
                .sorted(java.util.Comparator.comparingInt(PersonalRecord::getDistanceM))
                .limit(RECORDS_RETENUS)
                .map(r -> new RecordResume(r.getDistanceM(), r.getTempsSec(), r.getDate()))
                .toList();
    }

    /** Seules les blessures encore en jeu : une blessure resolue n'oriente plus rien. */
    private List<BlessureResume> blessures(UUID athleteId) {
        return profils.blessuresEnCours(athleteId).stream()
                .map(b -> new BlessureResume(b.getZone(), b.getStatut(), b.getPalier(), tronquer(b.getConsignes())))
                .toList();
    }

    /** Seules les contraintes qui portent sur la periode en cours ou a venir. */
    private List<ContrainteResume> contraintes(UUID athleteId) {
        LocalDate aujourdhui = LocalDate.now();
        return profils.contraintes(athleteId).stream()
                .filter(c -> c.getFin() == null || !c.getFin().isBefore(aujourdhui))
                .map(c -> new ContrainteResume(c.getType(), c.getDebut(), c.getFin(), tronquer(c.getDetail())))
                .toList();
    }

    private List<SignatureResume> signatures(UUID athleteId) {
        return profils.seancesSignature(athleteId).stream()
                .map(s -> new SignatureResume(
                        s.getNom(), s.getTypeSeance().name(), s.getFrequenceSouhaitee(), s.isActif()))
                .toList();
    }

    private CycleResume cycleResume(Cycle cycle) {
        List<TrainingWeek> semaines = cycles.semainesDe(cycle.getId());
        return new CycleResume(
                cycle.getId(), cycle.getNom(), cycle.getType().name(), cycle.getDateDebut(), cycle.getDateFin(),
                cycle.getCourseNom(), cycle.getCourseDate(), cycle.getChronoViseSec(), cycle.joursAvantCourse(),
                cycle.getLigneDirectrice(),
                cycle.getLigneDirectriceType() == null ? null : cycle.getLigneDirectriceType().name(),
                cycle.getHorizonSemaines(), cycle.getAlluresCibles(),
                (int) semaines.stream().filter(TrainingWeek::isDetaillee).count(),
                semaines.size());
    }

    /** La semaine en cours, avec ses seances : c'est sur elle que porte le point. */
    private SemaineResume semaineCourante(Cycle cycle) {
        LocalDate lundi = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        TrainingWeek semaine = cycles.semaineDe(cycle.getId(), lundi).orElse(null);
        if (semaine == null) {
            return null;
        }
        List<PlannedSession> deLaSemaine = cycles.seancesDe(cycle.getId()).stream()
                .filter(s -> s.getWeekId().equals(semaine.getId()))
                .toList();
        return new SemaineResume(
                semaine.getId(), (int) semaine.getNumero(), semaine.getDateDebut(),
                semaine.getBloc() == null ? null : semaine.getBloc().name(),
                semaine.getVolumeCibleKm(), PlannedSession.volumePlanifie(deLaSemaine),
                semaine.isDetaillee(), semaine.getNote(),
                deLaSemaine.stream().map(this::seanceResume).toList());
    }

    private SeanceResume seanceResume(PlannedSession s) {
        return new SeanceResume(
                s.getId(), s.getDate(), s.getType().name(), s.getTitre(), s.getStatut().name(),
                s.getDistanceCibleKm(), s.getAlluresTexte());
    }

    private List<NoteResume> memoire(UUID athleteId, Cycle cycle) {
        return notes.memoireUtile(athleteId, cycle == null ? null : cycle.getId()).stream()
                .map(n -> new NoteResume(
                        n.getId(), n.getDate(), n.getPortee().name(), n.getCategorie().name(), n.getTitre(),
                        n.getContenu()))
                .toList();
    }

    private List<JournalResume> journalRecent(UUID athleteId) {
        return journal.dernieres(athleteId, ENTREES_JOURNAL).stream()
                .map(e -> new JournalResume(
                        e.getDate(), tronquer(e.getContenu()), e.getHumeur(), e.getFatigue(), e.isDouleur()))
                .toList();
    }

    private DernierBilan dernierBilan(UUID athleteId) {
        WeeklyReport rapport = rapports.dernier(athleteId);
        return rapport == null
                ? null
                : new DernierBilan(rapport.getDateDebut(), tronquer(rapport.getBilan()), rapport.getConsignes());
    }

    /** Aucun champ de texte ne part en entier : le contexte doit rester lisible d'un coup d'oeil. */
    private static String tronquer(String texte) {
        int limite = 800;
        if (texte == null || texte.length() <= limite) {
            return texte;
        }
        return texte.substring(0, limite) + "…";
    }
}
