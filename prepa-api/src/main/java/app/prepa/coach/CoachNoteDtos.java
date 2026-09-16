package app.prepa.coach;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs des notes de coach et des bilans. */
public final class CoachNoteDtos {

    private CoachNoteDtos() {}

    public record CreateNoteRequest(
            LocalDate date,
            UUID cycleId,
            @NotNull CoachNote.Portee portee,
            @NotNull CoachNote.Categorie categorie,
            @NotBlank @Size(max = CoachNote.TITRE_MAX) String titre,
            @NotBlank @Size(max = CoachNote.CONTENU_MAX) String contenu,
            /** Note que celle-ci rend caduque : elle sera desactivee dans la meme operation. */
            UUID remplaceId) {}

    public record NoteResponse(
            UUID id,
            LocalDate date,
            UUID cycleId,
            CoachNote.Portee portee,
            CoachNote.Categorie categorie,
            String titre,
            String contenu,
            boolean actif,
            UUID remplaceId) {

        public static NoteResponse from(CoachNote n) {
            return new NoteResponse(
                    n.getId(), n.getDate(), n.getCycleId(), n.getPortee(), n.getCategorie(), n.getTitre(),
                    n.getContenu(), n.isActif(), n.getRemplaceId());
        }
    }

    public record CreateReportRequest(
            @NotNull LocalDate dateDebut,
            UUID weekId,
            @NotBlank String bilan,
            String pointsAttention,
            List<java.util.Map<String, Object>> changements,
            String consignes) {}

    public record ReportResponse(
            UUID id,
            LocalDate dateDebut,
            String bilan,
            String pointsAttention,
            List<java.util.Map<String, Object>> changements,
            String consignes) {

        public static ReportResponse from(WeeklyReport r) {
            return new ReportResponse(
                    r.getId(), r.getDateDebut(), r.getBilan(), r.getPointsAttention(), r.getChangements(),
                    r.getConsignes());
        }
    }
}
