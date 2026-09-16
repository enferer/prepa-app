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

    public record WeekResponse(
            UUID id,
            short numero,
            LocalDate dateDebut,
            BlocEntrainement bloc,
            BigDecimal volumeCibleKm,
            Short nbQualiteCible,
            Integer deniveleCibleM,
            boolean detaillee,
            String note,
            List<SessionResponse> seances) {}

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
            String rapprochement) {

        public static SessionResponse from(PlannedSession s) {
            return new SessionResponse(
                    s.getId(), s.getWeekId(), s.getDate(), s.getOrdre(), s.getType(), s.getTitre(),
                    s.getDescription(), s.getStatut(), s.getAlluresTexte(), s.getDistanceCibleKm(),
                    s.getDureeCibleMin(), s.getFocus(), s.getCommentaireCoach(), s.getCommentaireAthlete(),
                    s.getActivityId(), s.getRapprochement());
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
            String commentaireCoach,
            UUID signatureSessionId) {}

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
            String commentaireCoach,
            LocalDate date,
            Short ordre) {}

    public record LinkActivityRequest(UUID activityId) {}
}
