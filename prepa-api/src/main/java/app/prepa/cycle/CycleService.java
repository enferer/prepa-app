package app.prepa.cycle;

import app.prepa.domain.StatutCycle;
import app.prepa.domain.TypeCycle;
import app.prepa.infra.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cycle de vie des cycles d'entrainement : creation, transitions, cloture. */
@Service
public class CycleService {

    private final CycleRepository cycles;
    private final TrainingWeekRepository semaines;
    private final PlannedSessionRepository seances;

    public CycleService(
            CycleRepository cycles, TrainingWeekRepository semaines, PlannedSessionRepository seances) {
        this.cycles = cycles;
        this.semaines = semaines;
        this.seances = seances;
    }

    @Transactional(readOnly = true)
    public List<Cycle> lister(UUID athleteId) {
        return cycles.findByAthleteIdOrderByDateDebutDesc(athleteId);
    }

    @Transactional(readOnly = true)
    public Optional<Cycle> actif(UUID athleteId) {
        return cycles.findByAthleteIdAndStatut(athleteId, StatutCycle.ACTIF);
    }

    @Transactional(readOnly = true)
    public Cycle parId(UUID id) {
        return cycles.findById(id).orElseThrow(() -> ApiException.notFound("Cycle"));
    }

    @Transactional
    public Cycle creer(UUID athleteId, CycleDtos.CreateCycleRequest req) {
        if (cycles.existsByAthleteIdAndSlug(athleteId, req.slug())) {
            throw ApiException.conflit("Un cycle porte deja cet identifiant");
        }
        LocalDate debut = lundiDe(req.dateDebut());
        LocalDate fin = calculerFin(req, debut);

        Cycle cycle = new Cycle(UUID.randomUUID(), athleteId, req.slug(), req.nom(), req.type(), debut, fin);
        appliquerIntention(cycle, req);
        if (req.alluresCibles() != null) {
            cycle.setAlluresCibles(req.alluresCibles());
        }
        if (req.activer()) {
            cloturerCycleActif(athleteId);
            cycle.setStatut(StatutCycle.ACTIF);
        }
        return cycles.save(cycle);
    }

    @Transactional
    public Cycle mettreAJour(UUID cycleId, CycleDtos.UpdateCycleRequest req) {
        Cycle cycle = parId(cycleId);
        if (req.nom() != null) {
            cycle.setNom(req.nom());
        }
        if (req.dateFin() != null) {
            cycle.setDateFin(req.dateFin());
        }
        if (req.ligneDirectrice() != null) {
            cycle.setLigneDirectrice(req.ligneDirectrice());
        }
        if (req.ligneDirectriceType() != null) {
            cycle.setLigneDirectriceType(req.ligneDirectriceType());
        }
        if (req.horizonSemaines() != null) {
            cycle.setHorizonSemaines(req.horizonSemaines());
            cycle.setDateFin(cycle.getDateDebut().plusWeeks(req.horizonSemaines()));
        }
        if (req.alluresCibles() != null) {
            cycle.setAlluresCibles(req.alluresCibles());
        }
        if (req.chronoViseSec() != null) {
            cycle.setChronoViseSec(req.chronoViseSec());
        }
        return cycles.save(cycle);
    }

    /**
     * Bascule un cycle d'un type a l'autre : on choisit une course en cours de cycle libre,
     * ou on relache l'objectif apres l'avoir abandonne.
     */
    @Transactional
    public Cycle convertir(UUID cycleId, CycleDtos.ConvertCycleRequest req) {
        Cycle cycle = parId(cycleId);
        if (cycle.getType() == req.versType()) {
            throw ApiException.invalide("Le cycle est deja de ce type");
        }
        cycle.setType(req.versType());
        if (req.versType() == TypeCycle.PREPA) {
            exigerCourse(req.courseDate(), req.chronoViseSec());
            cycle.setCourseNom(req.courseNom());
            cycle.setCourseDate(req.courseDate());
            cycle.setCourseDistanceM(req.courseDistanceM());
            cycle.setChronoViseSec(req.chronoViseSec());
            cycle.setDateFin(req.courseDate());
            cycle.setLigneDirectrice(null);
            cycle.setLigneDirectriceType(null);
            cycle.setHorizonSemaines(null);
        } else {
            if (req.ligneDirectrice() == null || req.ligneDirectrice().isBlank()) {
                throw ApiException.invalide("Un cycle libre a besoin d'une ligne directrice");
            }
            cycle.setLigneDirectrice(req.ligneDirectrice());
            cycle.setLigneDirectriceType(req.ligneDirectriceType());
            short horizon = req.horizonSemaines() == null ? 12 : req.horizonSemaines();
            cycle.setHorizonSemaines(horizon);
            cycle.setDateFin(LocalDate.now().plusWeeks(horizon));
            cycle.setCourseNom(null);
            cycle.setCourseDate(null);
            cycle.setCourseDistanceM(null);
            cycle.setChronoViseSec(null);
        }
        return cycles.save(cycle);
    }

    @Transactional
    public Cycle cloturer(UUID cycleId, String bilan) {
        Cycle cycle = parId(cycleId);
        cycle.setStatut(StatutCycle.TERMINE);
        cycle.setBilan(bilan);
        return cycles.save(cycle);
    }

    @Transactional
    public Cycle activer(UUID cycleId) {
        Cycle cycle = parId(cycleId);
        cloturerCycleActif(cycle.getAthleteId());
        cycle.setStatut(StatutCycle.ACTIF);
        return cycles.save(cycle);
    }

    /** La semaine du plan qui couvre une date, s'il y en a une. */
    @Transactional(readOnly = true)
    public Optional<TrainingWeek> semaineDe(UUID cycleId, LocalDate jour) {
        return semaines.findByCycleIdAndDateDebut(cycleId, lundiDe(jour));
    }

    @Transactional(readOnly = true)
    public List<TrainingWeek> semainesDe(UUID cycleId) {
        return semaines.findByCycleIdOrderByNumeroAsc(cycleId);
    }

    @Transactional(readOnly = true)
    public List<PlannedSession> seancesDe(UUID cycleId) {
        return seances.findByCycleIdOrderByDateAscOrdreAsc(cycleId);
    }

    /**
     * Un seul cycle actif a la fois : demarrer le suivant termine le precedent.
     * La contrainte existe aussi en base, mais on prefere une transition explicite
     * a une violation d'index.
     */
    private void cloturerCycleActif(UUID athleteId) {
        actif(athleteId).ifPresent(precedent -> {
            precedent.setStatut(StatutCycle.TERMINE);
            cycles.save(precedent);
        });
    }

    private void appliquerIntention(Cycle cycle, CycleDtos.CreateCycleRequest req) {
        if (req.type() == TypeCycle.PREPA) {
            exigerCourse(req.courseDate(), req.chronoViseSec());
            cycle.setCourseNom(req.courseNom());
            cycle.setCourseDate(req.courseDate());
            cycle.setCourseDistanceM(req.courseDistanceM());
            cycle.setChronoViseSec(req.chronoViseSec());
        } else {
            if (req.ligneDirectrice() == null || req.ligneDirectrice().isBlank()) {
                throw ApiException.invalide("Un cycle libre a besoin d'une ligne directrice");
            }
            cycle.setLigneDirectrice(req.ligneDirectrice());
            cycle.setLigneDirectriceType(req.ligneDirectriceType());
            cycle.setHorizonSemaines(req.horizonSemaines());
        }
    }

    private static void exigerCourse(LocalDate courseDate, Integer chronoViseSec) {
        if (courseDate == null || chronoViseSec == null) {
            throw ApiException.invalide("Une preparation a besoin d'une date de course et d'un chrono vise");
        }
    }

    /**
     * Date de fin : la course pour une preparation, l'horizon pour un cycle libre.
     * Sans horizon declare, on retient douze semaines — un trimestre, l'echelle a laquelle
     * un bloc d'entrainement produit un effet visible.
     */
    private static LocalDate calculerFin(CycleDtos.CreateCycleRequest req, LocalDate debut) {
        if (req.dateFin() != null) {
            return req.dateFin();
        }
        if (req.type() == TypeCycle.PREPA) {
            return req.courseDate();
        }
        short horizon = req.horizonSemaines() == null ? 12 : req.horizonSemaines();
        return debut.plusWeeks(horizon);
    }

    /** Les semaines de plan commencent un lundi : on aligne la date fournie. */
    static LocalDate lundiDe(LocalDate jour) {
        return jour.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
