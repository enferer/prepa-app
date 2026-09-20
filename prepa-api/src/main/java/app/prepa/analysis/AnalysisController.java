package app.prepa.analysis;

import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.cycle.Cycle;
import app.prepa.cycle.CycleService;
import app.prepa.cycle.TrainingWeek;
import app.prepa.infra.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectures destinees au coach : synthese, contexte de depart, rapprochement d'une semaine.
 *
 * <p>Ces trois endpoints remplacent le travail de croisement de fichiers qui precedait chaque
 * point hebdomadaire.
 */
@RestController
@RequestMapping("/api/v1/athletes/{athleteId}")
public class AnalysisController {

    private final AnalysisService analyse;
    private final CoachContextService contexte;
    private final ReconciliationService rapprochement;
    private final CycleService cycles;
    private final AthleteService athletes;

    public AnalysisController(
            AnalysisService analyse,
            CoachContextService contexte,
            ReconciliationService rapprochement,
            CycleService cycles,
            AthleteService athletes) {
        this.analyse = analyse;
        this.contexte = contexte;
        this.rapprochement = rapprochement;
        this.cycles = cycles;
        this.athletes = athletes;
    }

    /** Tendances de fond : volume, allures reelles, efforts notables, comparaison de periodes. */
    @GetMapping("/analysis")
    public AnalysisDtos.Synthese synthese(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) Integer jours,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis) {
        autoriserLecture(athleteId);
        return analyse.analyser(athleteId, jours, depuis);
    }

    /**
     * Tout ce qu'il faut savoir pour ouvrir un point, et rien de plus.
     *
     * <p>Reste ferme aux autres athletes, contrairement au reste de cet ecran : le contexte
     * agrege le journal, les blessures et la memoire du coach — la personne, pas seulement sa
     * course.
     */
    @GetMapping("/coach-context")
    public CoachContextService.CoachContext contexte(@PathVariable UUID athleteId) {
        athletes.modifiable(athleteId, CurrentPrincipal.get());
        return contexte.construire(athleteId);
    }

    /**
     * Prevu contre realise sur une semaine, ecarts deja qualifies.
     * Sans parametre, la semaine visee est celle qui vient de s'ecouler — c'est celle dont on
     * fait le point.
     */
    @GetMapping("/reconciliation")
    public ReconciliationService.Rapprochement rapprochement(
            @PathVariable UUID athleteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semaine) {
        autoriserLecture(athleteId);
        Cycle cycle = cycles.actif(athleteId).orElseThrow(() -> ApiException.notFound("Cycle actif"));

        LocalDate lundi = semaine != null
                ? semaine.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : semaineEcoulee(LocalDate.now());
        TrainingWeek semainePlan = cycles.semaineDe(cycle.getId(), lundi).orElse(null);
        return rapprochement.rapprocher(cycle, lundi, lundi.plusDays(6), semainePlan);
    }

    /**
     * Lundi de la derniere semaine (lundi-dimanche) entierement ecoulee a la date donnee.
     *
     * <p>{@code aujourdhui.minusWeeks(1)} avant de caler sur le lundi se trompait le dimanche :
     * ce jour-la, {@code aujourdhui - 7 jours} tombe le dimanche precedent, qui cale sur le
     * lundi d'ENCORE une semaine plus tot — la semaine qui vient de s'achever (celle du jour
     * meme, dont le dimanche est aujourdhui) etait sautee entierement.
     */
    static LocalDate semaineEcoulee(LocalDate aujourdhui) {
        LocalDate lundiCourant = aujourdhui.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return aujourdhui.getDayOfWeek() == DayOfWeek.SUNDAY ? lundiCourant : lundiCourant.minusWeeks(1);
    }

    /** Volumes, allures et ecarts : de l'entrainement, donc lisible par les autres athletes. */
    private void autoriserLecture(UUID athleteId) {
        athletes.lisible(athleteId, CurrentPrincipal.get());
    }
}
