package app.prepa.cycle;

import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.auth.Principal;
import app.prepa.infra.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cycles et plan d'entrainement. */
@RestController
@RequestMapping("/api/v1")
public class CycleController {

    private final CycleService cycleService;
    private final PlanService planService;
    private final AthleteService athletes;

    public CycleController(CycleService cycleService, PlanService planService, AthleteService athletes) {
        this.cycleService = cycleService;
        this.planService = planService;
        this.athletes = athletes;
    }

    @GetMapping("/athletes/{athleteId}/cycles")
    public List<CycleDtos.CycleResponse> lister(@PathVariable UUID athleteId) {
        athletes.accessible(athleteId, CurrentPrincipal.get());
        return cycleService.lister(athleteId).stream()
                .map(CycleDtos.CycleResponse::from)
                .toList();
    }

    @GetMapping("/athletes/{athleteId}/cycles/actif")
    public CycleDtos.CycleDetailResponse actif(@PathVariable UUID athleteId) {
        athletes.accessible(athleteId, CurrentPrincipal.get());
        Cycle cycle = cycleService
                .actif(athleteId)
                .orElseThrow(() -> ApiException.notFound("Cycle actif"));
        return detail(cycle);
    }

    @PostMapping("/athletes/{athleteId}/cycles")
    public CycleDtos.CycleResponse creer(
            @PathVariable UUID athleteId, @Valid @RequestBody CycleDtos.CreateCycleRequest req) {
        exigerCoach(athleteId);
        return CycleDtos.CycleResponse.from(cycleService.creer(athleteId, req));
    }

    @GetMapping("/cycles/{cycleId}")
    public CycleDtos.CycleDetailResponse parId(@PathVariable UUID cycleId) {
        Cycle cycle = cycleService.parId(cycleId);
        athletes.accessible(cycle.getAthleteId(), CurrentPrincipal.get());
        return detail(cycle);
    }

    @PatchMapping("/cycles/{cycleId}")
    public CycleDtos.CycleResponse modifier(
            @PathVariable UUID cycleId, @Valid @RequestBody CycleDtos.UpdateCycleRequest req) {
        exigerCoachSurCycle(cycleId);
        return CycleDtos.CycleResponse.from(cycleService.mettreAJour(cycleId, req));
    }

    @PostMapping("/cycles/{cycleId}/convert")
    public CycleDtos.CycleResponse convertir(
            @PathVariable UUID cycleId, @Valid @RequestBody CycleDtos.ConvertCycleRequest req) {
        exigerCoachSurCycle(cycleId);
        return CycleDtos.CycleResponse.from(cycleService.convertir(cycleId, req));
    }

    @PostMapping("/cycles/{cycleId}/close")
    public CycleDtos.CycleResponse cloturer(
            @PathVariable UUID cycleId, @Valid @RequestBody CycleDtos.CloseCycleRequest req) {
        exigerCoachSurCycle(cycleId);
        return CycleDtos.CycleResponse.from(cycleService.cloturer(cycleId, req.bilan()));
    }

    @PostMapping("/cycles/{cycleId}/activate")
    public CycleDtos.CycleResponse activer(@PathVariable UUID cycleId) {
        exigerCoachSurCycle(cycleId);
        return CycleDtos.CycleResponse.from(cycleService.activer(cycleId));
    }

    @PutMapping("/cycles/{cycleId}/plan")
    public CycleDtos.CycleDetailResponse remplacerPlan(
            @PathVariable UUID cycleId, @Valid @RequestBody CycleDtos.PutPlanRequest req) {
        exigerCoachSurCycle(cycleId);
        planService.remplacerPlan(cycleId, req);
        return detail(cycleService.parId(cycleId));
    }

    @PostMapping("/weeks/{weekId}/sessions")
    public CycleDtos.SessionResponse ajouterSeance(
            @PathVariable UUID weekId, @Valid @RequestBody CycleDtos.SessionInput req) {
        PlannedSession creee = planService.ajouter(weekId, req);
        exigerCoachSurCycle(creee.getCycleId());
        return CycleDtos.SessionResponse.from(creee);
    }

    /**
     * Modification d'une seance. Le corps accepte est celui du demandeur : un athlete
     * n'expose que statut, commentaire et date ; un client de coach a la main sur tout le reste.
     */
    @PatchMapping("/sessions/{sessionId}")
    public CycleDtos.SessionResponse modifierSeance(
            @PathVariable UUID sessionId, @RequestBody CycleDtos.CoachSessionPatch patch) {
        Principal principal = CurrentPrincipal.get();
        PlannedSession seance = planService.seanceParId(sessionId);
        athletes.accessible(planService.athleteDe(seance), principal);

        if (principal.estCoach()) {
            return CycleDtos.SessionResponse.from(planService.modifierParCoach(sessionId, patch));
        }
        planService.exigerCoach(principal, patch);
        CycleDtos.AthleteSessionPatch propre =
                new CycleDtos.AthleteSessionPatch(patch.statut(), null, patch.date());
        return CycleDtos.SessionResponse.from(planService.modifierParAthlete(sessionId, propre));
    }

    /** Commentaire de l'athlete sur une seance — separe pour ne pas encombrer le patch coach. */
    @PatchMapping("/sessions/{sessionId}/commentaire")
    public CycleDtos.SessionResponse commenterSeance(
            @PathVariable UUID sessionId, @Valid @RequestBody CycleDtos.AthleteSessionPatch patch) {
        PlannedSession seance = planService.seanceParId(sessionId);
        athletes.accessible(planService.athleteDe(seance), CurrentPrincipal.get());
        return CycleDtos.SessionResponse.from(planService.modifierParAthlete(sessionId, patch));
    }

    @PostMapping("/sessions/{sessionId}/link-activity")
    public CycleDtos.SessionResponse lierActivite(
            @PathVariable UUID sessionId, @RequestBody CycleDtos.LinkActivityRequest req) {
        PlannedSession seance = planService.seanceParId(sessionId);
        athletes.accessible(planService.athleteDe(seance), CurrentPrincipal.get());
        return CycleDtos.SessionResponse.from(planService.lierActivite(sessionId, req.activityId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> supprimerSeance(@PathVariable UUID sessionId) {
        PlannedSession seance = planService.seanceParId(sessionId);
        exigerCoachSurCycle(seance.getCycleId());
        planService.supprimer(sessionId);
        return ResponseEntity.noContent().build();
    }

    private CycleDtos.CycleDetailResponse detail(Cycle cycle) {
        Map<UUID, List<CycleDtos.SessionResponse>> parSemaine = cycleService.seancesDe(cycle.getId()).stream()
                .collect(Collectors.groupingBy(
                        PlannedSession::getWeekId,
                        Collectors.mapping(CycleDtos.SessionResponse::from, Collectors.toList())));

        List<CycleDtos.WeekResponse> semaines = cycleService.semainesDe(cycle.getId()).stream()
                .map(s -> new CycleDtos.WeekResponse(
                        s.getId(), s.getNumero(), s.getDateDebut(), s.getBloc(), s.getVolumeCibleKm(),
                        s.getNbQualiteCible(), s.getDeniveleCibleM(), s.isDetaillee(), s.getNote(),
                        parSemaine.getOrDefault(s.getId(), List.of())))
                .toList();

        return new CycleDtos.CycleDetailResponse(CycleDtos.CycleResponse.from(cycle), semaines);
    }

    /** Concevoir un cycle ou un plan releve du coach, jamais de l'athlete. */
    private void exigerCoach(UUID athleteId) {
        Principal principal = CurrentPrincipal.get();
        athletes.accessible(athleteId, principal);
        if (!principal.estCoach()) {
            throw ApiException.forbidden("La construction du plan releve de ton coach");
        }
    }

    private void exigerCoachSurCycle(UUID cycleId) {
        exigerCoach(cycleService.parId(cycleId).getAthleteId());
    }
}
