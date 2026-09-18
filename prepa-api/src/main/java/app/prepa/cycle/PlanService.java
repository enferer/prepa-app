package app.prepa.cycle;

import app.prepa.auth.Principal;
import app.prepa.domain.StatutSeance;
import app.prepa.domain.TypeSeance;
import app.prepa.infra.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ecriture du plan.
 *
 * <p>Porte la ligne de partage entre ce qu'un athlete fait lui-meme et ce qui releve du
 * coach : <em>l'athlete decrit la realite, il ne redessine pas l'entrainement</em>. Marquer
 * une seance faite, la commenter, la decaler dans sa semaine — oui. Changer son type, ses
 * allures ou sa distance cible — non : ce sont des decisions qui demandent de relire la
 * progression d'ensemble.
 */
@Service
public class PlanService {

    private final CycleRepository cycles;
    private final TrainingWeekRepository semaines;
    private final PlannedSessionRepository seances;

    public PlanService(
            CycleRepository cycles, TrainingWeekRepository semaines, PlannedSessionRepository seances) {
        this.cycles = cycles;
        this.semaines = semaines;
        this.seances = seances;
    }

    /**
     * Remplace le plan d'un cycle en une transaction.
     *
     * <p>Les seances deja vecues ne sont pas effacees pour autant : leur statut, leur
     * rapprochement a une activite et le commentaire de l'athlete sont reportes sur la
     * nouvelle version. Regenerer un plan glissant ne doit jamais faire perdre l'historique.
     */
    @Transactional
    public void remplacerPlan(UUID cycleId, CycleDtos.PutPlanRequest req) {
        Cycle cycle = cycles.findById(cycleId).orElseThrow(() -> ApiException.notFound("Cycle"));
        Map<String, PlannedSession> ancien = indexerParEmpreinte(seances.findByCycleIdOrderByDateAscOrdreAsc(cycleId));

        semaines.deleteByCycleId(cycleId);
        semaines.flush();

        for (CycleDtos.WeekInput semaineInput : req.semaines()) {
            TrainingWeek semaine = new TrainingWeek(
                    UUID.randomUUID(),
                    cycleId,
                    semaineInput.numero(),
                    CycleService.lundiDe(semaineInput.dateDebut()),
                    semaineInput.volumeCibleKm());
            semaine.setBloc(semaineInput.bloc());
            semaine.setNbQualiteCible(semaineInput.nbQualiteCible());
            semaine.setDeniveleCibleM(semaineInput.deniveleCibleM());
            semaine.setDetaillee(semaineInput.detaillee() == null || semaineInput.detaillee());
            semaine.setNote(semaineInput.note());
            semaines.save(semaine);

            if (semaineInput.seances() == null) {
                continue;
            }
            for (CycleDtos.SessionInput entree : semaineInput.seances()) {
                PlannedSession seance = construire(cycle, semaine, entree);
                reporterHistorique(ancien, seance);
                seances.save(seance);
            }
        }
    }

    /**
     * Modification d'une semaine : ce qu'elle vise, son bloc, sa note.
     *
     * <p>La cible de volume ne se deduit pas des seances, et ce n'est pas un oubli. Une
     * semaine non detaillee n'a que sa cible — en cycle glissant, c'est meme sa seule
     * substance. Et sur une semaine detaillee, recalculer la cible a chaque retouche de
     * seance reviendrait a effacer l'intention du coach chaque fois qu'il ajuste un footing :
     * l'ecart entre ce qui est vise et ce qui est pose serait toujours nul, donc jamais
     * lisible. On l'expose (voir {@code volumePlanifieKm}) plutot que de le faire disparaitre.
     */
    @Transactional
    public TrainingWeek modifierSemaine(UUID weekId, CycleDtos.UpdateWeekRequest patch) {
        TrainingWeek semaine = semaineParId(weekId);
        if (patch.bloc() != null) {
            semaine.setBloc(patch.bloc());
        }
        if (patch.volumeCibleKm() != null) {
            semaine.setVolumeCibleKm(patch.volumeCibleKm());
        }
        if (patch.nbQualiteCible() != null) {
            semaine.setNbQualiteCible(patch.nbQualiteCible());
        }
        if (patch.deniveleCibleM() != null) {
            semaine.setDeniveleCibleM(patch.deniveleCibleM());
        }
        if (patch.detaillee() != null) {
            semaine.setDetaillee(patch.detaillee());
        }
        if (patch.note() != null) {
            semaine.setNote(patch.note());
        }
        return semaines.save(semaine);
    }

    @Transactional(readOnly = true)
    public TrainingWeek semaineParId(UUID weekId) {
        return semaines.findById(weekId).orElseThrow(() -> ApiException.notFound("Semaine"));
    }

    /** Les seances d'une semaine, dans l'ordre ou elles se courent. */
    @Transactional(readOnly = true)
    public List<PlannedSession> seancesDeLaSemaine(UUID weekId) {
        return seances.findByWeekIdOrderByDateAscOrdreAsc(weekId);
    }

    @Transactional
    public PlannedSession ajouter(UUID weekId, CycleDtos.SessionInput entree) {
        TrainingWeek semaine = semaineParId(weekId);
        Cycle cycle = cycles.findById(semaine.getCycleId()).orElseThrow(() -> ApiException.notFound("Cycle"));
        return seances.save(construire(cycle, semaine, entree));
    }

    @Transactional
    public void supprimer(UUID sessionId) {
        seances.delete(seanceParId(sessionId));
    }

    /** Modification par l'athlete : statut, commentaire, et deplacement dans sa propre semaine. */
    @Transactional
    public PlannedSession modifierParAthlete(UUID sessionId, CycleDtos.AthleteSessionPatch patch) {
        PlannedSession seance = seanceParId(sessionId);
        if (patch.statut() != null) {
            seance.setStatut(patch.statut());
        }
        if (patch.commentaireAthlete() != null) {
            seance.setCommentaireAthlete(patch.commentaireAthlete());
        }
        if (patch.date() != null && !patch.date().equals(seance.getDate())) {
            deplacerDansSaSemaine(seance, patch.date());
        }
        return seances.save(seance);
    }

    /** Modification par le coach : tout le reste. */
    @Transactional
    public PlannedSession modifierParCoach(UUID sessionId, CycleDtos.CoachSessionPatch patch) {
        PlannedSession seance = seanceParId(sessionId);
        if (patch.statut() != null) {
            seance.setStatut(patch.statut());
        }
        if (patch.type() != null) {
            seance.setType(patch.type());
        }
        if (patch.titre() != null) {
            seance.setTitre(patch.titre());
        }
        if (patch.description() != null) {
            seance.setDescription(patch.description());
        }
        if (patch.alluresTexte() != null) {
            seance.setAlluresTexte(patch.alluresTexte());
        }
        if (patch.distanceCibleKm() != null) {
            seance.setDistanceCibleKm(patch.distanceCibleKm());
        }
        if (patch.dureeCibleMin() != null) {
            seance.setDureeCibleMin(patch.dureeCibleMin());
        }
        if (patch.focus() != null) {
            seance.setFocus(patch.focus());
        }
        if (patch.commentaireCoach() != null) {
            seance.setCommentaireCoach(patch.commentaireCoach());
        }
        if (patch.ordre() != null) {
            seance.setOrdre(patch.ordre());
        }
        if (patch.date() != null) {
            rattacherASemaine(seance, patch.date());
        }
        exigerFocusSiRenfo(seance);
        return seances.save(seance);
    }

    /** Rapprochement manuel d'une seance et d'une activite. */
    @Transactional
    public PlannedSession lierActivite(UUID sessionId, UUID activityId) {
        PlannedSession seance = seanceParId(sessionId);
        if (activityId == null) {
            seance.detacher();
        } else {
            seances.findByActivityId(activityId)
                    .filter(autre -> !autre.getId().equals(sessionId))
                    .ifPresent(PlannedSession::detacher);
            seance.rapprocherDe(activityId, true);
            // Rapprocher une seance d'une activite, c'est constater qu'elle a eu lieu — sans
            // pour autant la juger : cela reste au coach.
            if (seance.getStatut().accepteUnConstatAutomatique()) {
                seance.setStatut(StatutSeance.REALISEE);
            }
        }
        return seances.save(seance);
    }

    @Transactional(readOnly = true)
    public PlannedSession seanceParId(UUID sessionId) {
        return seances.findById(sessionId).orElseThrow(() -> ApiException.notFound("Séance"));
    }

    /** L'athlete de rattachement d'une seance, pour le controle d'acces. */
    @Transactional(readOnly = true)
    public UUID athleteDe(PlannedSession seance) {
        return cycles.findById(seance.getCycleId())
                .map(Cycle::getAthleteId)
                .orElseThrow(() -> ApiException.notFound("Cycle"));
    }

    /**
     * Refuse une modification de coach demandee par un athlete, en nommant le champ fautif
     * plutot qu'en renvoyant un refus opaque.
     */
    public void exigerCoach(Principal principal, CycleDtos.CoachSessionPatch patch) {
        if (principal.estCoach()) {
            return;
        }
        Map<String, Object> reserves = new HashMap<>();
        reserves.put("type", patch.type());
        reserves.put("titre", patch.titre());
        reserves.put("description", patch.description());
        reserves.put("alluresTexte", patch.alluresTexte());
        reserves.put("distanceCibleKm", patch.distanceCibleKm());
        reserves.put("dureeCibleMin", patch.dureeCibleMin());
        reserves.put("focus", patch.focus());
        reserves.put("commentaireCoach", patch.commentaireCoach());
        reserves.put("ordre", patch.ordre());
        reserves.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .findFirst()
                .ifPresent(e -> {
                    throw ApiException.forbiddenField(e.getKey());
                });
    }

    /**
     * Un athlete peut decaler une seance, mais a l'interieur de sa semaine : deplacer une
     * sortie longue d'une semaine sur l'autre revient a modifier la charge de deux semaines.
     */
    private void deplacerDansSaSemaine(PlannedSession seance, LocalDate nouvelleDate) {
        TrainingWeek semaine = semaines.findById(seance.getWeekId())
                .orElseThrow(() -> ApiException.notFound("Semaine"));
        if (!semaine.contient(nouvelleDate)) {
            throw ApiException.forbidden(
                    "Tu peux décaler une séance dans sa semaine ; pour la déplacer plus loin, demande à ton coach");
        }
        seance.setDate(nouvelleDate);
        seance.setStatut(StatutSeance.DEPLACEE);
    }

    /** Le coach peut deplacer une seance n'importe ou : elle change alors de semaine. */
    private void rattacherASemaine(PlannedSession seance, LocalDate nouvelleDate) {
        seance.setDate(nouvelleDate);
        semaines.findByCycleIdAndDateDebut(seance.getCycleId(), CycleService.lundiDe(nouvelleDate))
                .ifPresent(semaine -> seance.setWeekId(semaine.getId()));
    }

    private PlannedSession construire(Cycle cycle, TrainingWeek semaine, CycleDtos.SessionInput entree) {
        PlannedSession seance = new PlannedSession(
                UUID.randomUUID(), semaine.getId(), cycle.getId(), entree.date(), entree.type(), entree.titre());
        seance.setOrdre(entree.ordre() == null ? (short) 0 : entree.ordre());
        seance.setDescription(entree.description());
        seance.setStatut(entree.statut() == null ? StatutSeance.A_VENIR : entree.statut());
        seance.setAlluresTexte(entree.alluresTexte());
        seance.setDistanceCibleKm(entree.distanceCibleKm());
        seance.setDureeCibleMin(entree.dureeCibleMin());
        seance.setFocus(entree.focus());
        seance.setCommentaireCoach(entree.commentaireCoach());
        seance.setSignatureSessionId(entree.signatureSessionId());
        exigerFocusSiRenfo(seance);
        return seance;
    }

    private static void exigerFocusSiRenfo(PlannedSession seance) {
        if (seance.getType() == TypeSeance.RENFO && (seance.getFocus() == null || seance.getFocus().isBlank())) {
            throw ApiException.invalide("Une séance de renforcement a besoin d'un focus");
        }
    }

    /** Une seance se reconnait d'une version du plan a l'autre par sa date, son type et son rang. */
    private static Map<String, PlannedSession> indexerParEmpreinte(List<PlannedSession> anciennes) {
        Map<String, PlannedSession> index = new HashMap<>();
        for (PlannedSession seance : anciennes) {
            index.put(empreinte(seance.getDate(), seance.getType(), seance.getOrdre()), seance);
        }
        return index;
    }

    private void reporterHistorique(Map<String, PlannedSession> ancien, PlannedSession nouvelle) {
        PlannedSession precedente =
                ancien.get(empreinte(nouvelle.getDate(), nouvelle.getType(), nouvelle.getOrdre()));
        if (precedente == null) {
            return;
        }
        if (precedente.getActivityId() != null) {
            nouvelle.rapprocherDe(precedente.getActivityId(), "MANUEL".equals(precedente.getRapprochement()));
            nouvelle.setStatut(precedente.getStatut());
        } else if (precedente.getStatut().estTranchee()) {
            nouvelle.setStatut(precedente.getStatut());
        }
        if (precedente.getCommentaireAthlete() != null) {
            nouvelle.setCommentaireAthlete(precedente.getCommentaireAthlete());
        }
    }

    private static String empreinte(LocalDate date, TypeSeance type, short ordre) {
        return date + "|" + type + "|" + ordre;
    }

    /** Les seances non encore rapprochees, utiles au rapprochement automatique. */
    @Transactional(readOnly = true)
    public List<PlannedSession> aRapprocher(UUID cycleId, LocalDate debut, LocalDate fin) {
        List<PlannedSession> resultat = new ArrayList<>();
        for (PlannedSession seance : seances.entre(cycleId, debut, fin)) {
            if (seance.getActivityId() == null && seance.estCourseAPied()) {
                resultat.add(seance);
            }
        }
        return resultat;
    }
}
