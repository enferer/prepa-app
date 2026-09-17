package app.prepa.activity;

import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.cycle.Cycle;
import app.prepa.cycle.CycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ActivityController {

    private final ActivityService activites;
    private final AthleteService athletes;
    private final CycleService cycles;

    public ActivityController(ActivityService activites, AthleteService athletes, CycleService cycles) {
        this.activites = activites;
        this.athletes = athletes;
        this.cycles = cycles;
    }

    @GetMapping("/athletes/{athleteId}/activities")
    public List<ActivityDtos.ActivityResume> lister(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    java.time.LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    java.time.LocalDate fin) {
        autoriser(athleteId);
        List<Activity> resultat = debut != null && fin != null
                ? activites.entre(athleteId, debut, fin)
                : activites.lister(athleteId);
        return resultat.stream().map(ActivityDtos.ActivityResume::from).toList();
    }

    /**
     * Activites jamais passees en revue : point de depart du bilan hebdomadaire.
     *
     * <p>Bornees au cycle en cours par defaut — c'est la periode sur laquelle le coach
     * raisonne. {@code depuis} permet de remonter plus loin quand on reprend un historique.
     */
    @GetMapping("/athletes/{athleteId}/activities/new")
    public List<ActivityDtos.ActivityDetail> nouvelles(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    java.time.LocalDate depuis) {
        autoriser(athleteId);
        java.time.LocalDate borne = depuis != null
                ? depuis
                : cycles.actif(athleteId)
                        .map(Cycle::getDateDebut)
                        .orElseGet(() -> java.time.LocalDate.now().minusWeeks(8));
        return activites.nonAnalysees(athleteId, borne).stream()
                .map(a -> activites.detail(a.getId()))
                .toList();
    }

    @PostMapping("/athletes/{athleteId}/activities/mark-analyzed")
    public MarkAnalyzedResponse marquerAnalysees(
            @PathVariable UUID athleteId, @RequestBody MarkAnalyzedRequest req) {
        autoriser(athleteId);
        return new MarkAnalyzedResponse(activites.marquerAnalysees(athleteId, req.activityIds()));
    }

    @GetMapping("/activities/{activityId}")
    public ActivityDtos.ActivityDetail detail(@PathVariable UUID activityId) {
        autoriser(activites.parId(activityId).getAthleteId());
        return activites.detail(activityId);
    }

    @PatchMapping("/activities/{activityId}/feedback")
    public ActivityDtos.ActivityResume ressenti(
            @PathVariable UUID activityId, @Valid @RequestBody ActivityDtos.FeedbackRequest req) {
        autoriser(activites.parId(activityId).getAthleteId());
        return ActivityDtos.ActivityResume.from(activites.enregistrerRessenti(activityId, req));
    }

    public record MarkAnalyzedRequest(List<UUID> activityIds) {}

    public record MarkAnalyzedResponse(int marquees) {}

    private void autoriser(UUID athleteId) {
        athletes.accessible(athleteId, CurrentPrincipal.get());
    }
}
