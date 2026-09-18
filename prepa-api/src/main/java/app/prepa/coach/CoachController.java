package app.prepa.coach;

import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.auth.Principal;
import app.prepa.cycle.Cycle;
import app.prepa.cycle.CycleService;
import app.prepa.infra.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Memoire et bilans du coach.
 *
 * <p>L'athlete peut les lire — ce sont ses decisions d'entrainement — mais seul un client de
 * coach les ecrit.
 */
@RestController
@RequestMapping("/api/v1")
public class CoachController {

    private final CoachNoteService notes;
    private final WeeklyReportService rapports;
    private final CycleService cycles;
    private final AthleteService athletes;

    public CoachController(
            CoachNoteService notes,
            WeeklyReportService rapports,
            CycleService cycles,
            AthleteService athletes) {
        this.notes = notes;
        this.rapports = rapports;
        this.cycles = cycles;
        this.athletes = athletes;
    }

    @GetMapping("/athletes/{athleteId}/coach-notes")
    public List<CoachNoteDtos.NoteResponse> lister(@PathVariable UUID athleteId) {
        athletes.modifiable(athleteId, CurrentPrincipal.get());
        return notes.toutes(athleteId).stream().map(CoachNoteDtos.NoteResponse::from).toList();
    }

    @PostMapping("/athletes/{athleteId}/coach-notes")
    public CoachNoteDtos.NoteResponse creer(
            @PathVariable UUID athleteId, @Valid @RequestBody CoachNoteDtos.CreateNoteRequest req) {
        exigerCoach(athleteId);
        return CoachNoteDtos.NoteResponse.from(notes.creer(athleteId, req));
    }

    @PatchMapping("/coach-notes/{noteId}/deactivate")
    public CoachNoteDtos.NoteResponse desactiver(@PathVariable UUID noteId) {
        exigerCoach(notes.parId(noteId).getAthleteId());
        return CoachNoteDtos.NoteResponse.from(notes.desactiver(noteId));
    }

    @GetMapping("/cycles/{cycleId}/reports")
    public List<CoachNoteDtos.ReportResponse> rapports(@PathVariable UUID cycleId) {
        Cycle cycle = cycles.parId(cycleId);
        athletes.modifiable(cycle.getAthleteId(), CurrentPrincipal.get());
        return rapports.duCycle(cycleId).stream().map(CoachNoteDtos.ReportResponse::from).toList();
    }

    @PostMapping("/cycles/{cycleId}/reports")
    public CoachNoteDtos.ReportResponse enregistrer(
            @PathVariable UUID cycleId, @Valid @RequestBody CoachNoteDtos.CreateReportRequest req) {
        Cycle cycle = cycles.parId(cycleId);
        exigerCoach(cycle.getAthleteId());
        return CoachNoteDtos.ReportResponse.from(rapports.enregistrer(cycle.getAthleteId(), cycleId, req));
    }

    private void exigerCoach(UUID athleteId) {
        Principal principal = CurrentPrincipal.get();
        athletes.modifiable(athleteId, principal);
        if (!principal.estCoach()) {
            throw ApiException.forbidden("Seul ton coach écrit ici");
        }
    }
}
