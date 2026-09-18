package app.prepa.cycle;

import app.prepa.domain.BlocEntrainement;
import app.prepa.domain.LigneDirectrice;
import app.prepa.domain.StatutCycle;
import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeCycle;
import app.prepa.domain.TypeSeance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs des cycles et du plan. */
public final class CycleDtos {

    private CycleDtos() {}

    /** Creation d'un cycle. Les champs exiges dependent du type — voir la validation du service. */
    public record CreateCycleRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9-]+", message = "slug en minuscules, chiffres et tirets")
                    String slug,
            @NotBlank String nom,
            @NotNull TypeCycle type,
            @NotNull LocalDate dateDebut,
            LocalDate dateFin,
            String courseNom,
            LocalDate courseDate,
            Integer courseDistanceM,
            Integer chronoViseSec,
            String ligneDirectrice,
            LigneDirectrice ligneDirectriceType,
            Short horizonSemaines,
            Map<String, Object> alluresCibles,
            boolean activer) {}

    /** Modification d'un cycle. Reservee au coach. */
    public record UpdateCycleRequest(
            String nom,
            LocalDate dateFin,
            String ligneDirectrice,
            LigneDirectrice ligneDirectriceType,
            Short horizonSemaines,
            Map<String, Object> alluresCibles,
            Integer chronoViseSec) {}

    /** Conversion d'un cycle libre en preparation, ou l'inverse. */
    public record ConvertCycleRequest(
            @NotNull TypeCycle versType,
            String courseNom,
            LocalDate courseDate,
            Integer courseDistanceM,
            Integer chronoViseSec,
            String ligneDirectrice,
            LigneDirectrice ligneDirectriceType,
            Short horizonSemaines) {}

    public record CloseCycleRequest(@NotBlank String bilan) {}

    public record CycleResponse(
            UUID id,
            String slug,
            String nom,
            TypeCycle type,
            StatutCycle statut,
            LocalDate dateDebut,
            LocalDate dateFin,
            String courseNom,
            LocalDate courseDate,
            Integer courseDistanceM,
            Integer chronoViseSec,
            Long joursAvantCourse,
            String ligneDirectrice,
            LigneDirectrice ligneDirectriceType,
            Short horizonSemaines,
            Map<String, Object> alluresCibles,
            String bilan,
            int nbSemaines) {

        public static CycleResponse from(Cycle c) {
            return new CycleResponse(
                    c.getId(), c.getSlug(), c.getNom(), c.getType(), c.getStatut(), c.getDateDebut(), c.getDateFin(),
                    c.getCourseNom(), c.getCourseDate(), c.getCourseDistanceM(), c.getChronoViseSec(),
                    c.joursAvantCourse(), c.getLigneDirectrice(), c.getLigneDirectriceType(), c.getHorizonSemaines(),
                    c.getAlluresCibles(), c.getBilan(), c.nbSemaines());
        }
    }

    /** Le cycle avec son plan complet. */
    public record CycleDetailResponse(CycleResponse cycle, List<WeekResponse> semaines) {}

    /**
     * Une semaine du plan.
     *
     * <p>Deux volumes y figurent, et ils ne disent pas la meme chose. {@code volumeCibleKm}
     * est l'<em>intention</em> du coach pour la semaine — la charge qu'il vise, seule
     * information disponible tant que la semaine n'est pas detaillee. {@code volumePlanifieKm}
     * est ce que les seances totalisent une fois posees. Les deux divergent des qu'on retouche
     * une seance, et c'est normal : la cible ne se recalcule pas toute seule, sinon deplacer un
     * kilometre d'un footing reecrirait silencieusement l'intention de la semaine. Les exposer
     * cote a cote rend l'ecart visible plutot que cache — au coach ensuite de trancher : ajuster
     * les seances, ou assumer la nouvelle cible avec {@code PATCH /weeks/&#123;id&#125;}.
     *
     * <p>Ni l'un ni l'autre n'est le volume <em>realise</em> : celui-la se calcule a partir des
     * activites et vit dans le rapprochement et l'analyse.
     */
    public record WeekResponse(
            UUID id,
            short numero,
            LocalDate dateDebut,
            BlocEntrainement bloc,
            BigDecimal volumeCibleKm,
            BigDecimal volumePlanifieKm,
            Short nbQualiteCible,
            Integer deniveleCibleM,
            boolean detaillee,
            String note,
            List<SessionResponse> seances) {

        public static WeekResponse from(TrainingWeek semaine, List<PlannedSession> seances) {
            return new WeekResponse(
                    semaine.getId(), semaine.getNumero(), semaine.getDateDebut(), semaine.getBloc(),
                    semaine.getVolumeCibleKm(), PlannedSession.volumePlanifie(seances),
                    semaine.getNbQualiteCible(), semaine.getDeniveleCibleM(), semaine.isDetaillee(),
                    semaine.getNote(), seances.stream().map(SessionResponse::from).toList());
        }
    }

    /**
     * Modification d'une semaine : ses cibles, son bloc, sa note. Reservee au coach.
     *
     * <p>Un champ absent est un champ inchange. Ajuster {@code volumeCibleKm} est un acte
     * delibere — on redit ce que la semaine vise — et non la consequence mecanique d'une
     * seance retouchee.
     */
    public record UpdateWeekRequest(
            BlocEntrainement bloc,
            @Positive BigDecimal volumeCibleKm,
            Short nbQualiteCible,
            Integer deniveleCibleM,
            Boolean detaillee,
            String note) {}

    public record SessionResponse(
            UUID id,
            UUID weekId,
            LocalDate date,
            short ordre,
            TypeSeance type,
            String titre,
            String description,
            StatutSeance statut,
            String alluresTexte,
            BigDecimal distanceCibleKm,
            Short dureeCibleMin,
            String focus,
            String commentaireCoach,
            String commentaireAthlete,
            UUID activityId,
            String rapprochement,
            /**
             * Le deroule, pret a dessiner : pose par le coach, ou relu dans la description a
             * defaut — voir {@link StructureSeance}.
             */
            List<StructureSeance.BlocPrevu> structure,
            /** Vrai quand le deroule vient du coach, faux quand il a fallu relire la phrase. */
            boolean structureSaisie) {

        public static SessionResponse from(PlannedSession s) {
            return new SessionResponse(
                    s.getId(), s.getWeekId(), s.getDate(), s.getOrdre(), s.getType(), s.getTitre(),
                    s.getDescription(), s.getStatut(), s.getAlluresTexte(), s.getDistanceCibleKm(),
                    s.getDureeCibleMin(), s.getFocus(), s.getCommentaireCoach(), s.getCommentaireAthlete(),
                    s.getActivityId(), s.getRapprochement(), StructureSeance.deduire(s),
                    s.getStructure() != null && !s.getStructure().isEmpty());
        }
    }

    /** Remplacement transactionnel du plan. Reserve au coach. */
    public record PutPlanRequest(@NotNull @Valid List<WeekInput> semaines) {}

    public record WeekInput(
            @NotNull Short numero,
            @NotNull LocalDate dateDebut,
            BlocEntrainement bloc,
            @NotNull @Positive BigDecimal volumeCibleKm,
            Short nbQualiteCible,
            Integer deniveleCibleM,
            Boolean detaillee,
            String note,
            @Valid List<SessionInput> seances) {}

    public record SessionInput(
            @NotNull LocalDate date,
            Short ordre,
            @NotNull TypeSeance type,
            @NotBlank String titre,
            String description,
            StatutSeance statut,
            String alluresTexte,
            BigDecimal distanceCibleKm,
            Short dureeCibleMin,
            String focus,
            @Size(max = PlannedSession.COMMENTAIRE_COACH_MAX) String commentaireCoach,
            UUID signatureSessionId,
            /** Le deroule, bloc par bloc. Laisse vide, il sera relu dans la description. */
            @Valid List<BlocSeance> structure) {}

    /** Modifications qu'un athlete peut faire lui-meme sur une seance. */
    public record AthleteSessionPatch(
            StatutSeance statut,
            @Size(max = 2000) String commentaireAthlete,
            LocalDate date) {}

    /** Modifications reservees au coach : tout ce qui touche a la conception de la seance. */
    public record CoachSessionPatch(
            StatutSeance statut,
            TypeSeance type,
            String titre,
            String description,
            String alluresTexte,
            BigDecimal distanceCibleKm,
            Short dureeCibleMin,
            String focus,
            @Size(max = PlannedSession.COMMENTAIRE_COACH_MAX) String commentaireCoach,
            LocalDate date,
            Short ordre,
            @Valid List<BlocSeance> structure) {}

    public record LinkActivityRequest(UUID activityId) {}
}
