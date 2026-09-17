package app.prepa.coach;

import app.prepa.infra.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memoire du coach.
 *
 * <p>Tout l'enjeu est qu'elle reste courte a relire. Le champ de commentaires cumulatif qu'elle
 * remplace avait fini par peser plusieurs milliers de caracteres qu'il fallait parcourir en
 * entier a chaque point hebdomadaire, sans pouvoir distinguer une regle toujours valable d'une
 * remarque d'il y a trois mois.
 *
 * <p>Trois mecanismes l'en empechent : une portee qui fixe la duree de vie de chaque note, une
 * longueur plafonnee, et un remplacement explicite quand une decision en annule une autre.
 */
@Service
public class CoachNoteService {

    /** Au-dela, la memoire durable devient elle-meme illisible et demande une consolidation. */
    public static final int SEUIL_ALERTE_DURABLES = 15;

    /** Nombre de notes ponctuelles remontees dans le contexte du coach. */
    private static final int PONCTUELLES_RETENUES = 10;

    /** Duree de vie des notes ponctuelles avant purge. */
    private static final int SEMAINES_RETENTION_PONCTUELLES = 8;

    private final CoachNoteRepository notes;

    public CoachNoteService(CoachNoteRepository notes) {
        this.notes = notes;
    }

    @Transactional
    public CoachNote creer(UUID athleteId, CoachNoteDtos.CreateNoteRequest req) {
        CoachNote note = new CoachNote(
                UUID.randomUUID(),
                athleteId,
                req.date() == null ? LocalDate.now() : req.date(),
                req.portee(),
                req.categorie(),
                req.titre(),
                req.contenu());
        note.setCycleId(req.cycleId());

        if (req.remplaceId() != null) {
            CoachNote precedente = notes.findById(req.remplaceId())
                    .orElseThrow(() -> ApiException.notFound("Note remplacée"));
            if (!precedente.getAthleteId().equals(athleteId)) {
                throw ApiException.notFound("Note remplacée");
            }
            precedente.desactiver();
            notes.save(precedente);
            note.setRemplaceId(precedente.getId());
        }
        return notes.save(note);
    }

    @Transactional(readOnly = true)
    public CoachNote parId(UUID noteId) {
        return notes.findById(noteId).orElseThrow(() -> ApiException.notFound("Note"));
    }

    @Transactional
    public CoachNote desactiver(UUID noteId) {
        CoachNote note = parId(noteId);
        note.desactiver();
        return notes.save(note);
    }

    @Transactional(readOnly = true)
    public List<CoachNote> toutes(UUID athleteId) {
        return notes.findByAthleteIdOrderByDateDesc(athleteId);
    }

    /**
     * Les notes que le coach doit avoir en tete, et rien de plus : les regles durables encore
     * actives, celles attachees au cycle en cours, et les dernieres notes ponctuelles.
     */
    @Transactional(readOnly = true)
    public List<CoachNote> memoireUtile(UUID athleteId, UUID cycleId) {
        List<CoachNote> retenues = new ArrayList<>(
                notes.findByAthleteIdAndPorteeAndActifTrueOrderByDateDesc(athleteId, CoachNote.Portee.DURABLE));
        if (cycleId != null) {
            retenues.addAll(notes.findByCycleIdAndPorteeAndActifTrueOrderByDateDesc(cycleId, CoachNote.Portee.CYCLE));
        }
        retenues.addAll(notes.findByAthleteIdAndPorteeAndActifTrueOrderByDateDesc(
                athleteId, CoachNote.Portee.PONCTUELLE, Limit.of(PONCTUELLES_RETENUES)));
        return retenues;
    }

    /**
     * Signale une memoire durable trop chargee. Ce n'est pas une erreur : c'est un signal
     * au coach qu'il est temps de consolider plutot que d'empiler.
     */
    @Transactional(readOnly = true)
    public String alerteVolumetrie(UUID athleteId) {
        long durables = notes.countByAthleteIdAndPorteeAndActifTrue(athleteId, CoachNote.Portee.DURABLE);
        return durables > SEUIL_ALERTE_DURABLES
                ? durables + " règles durables actives : il est temps d'en consolider ou d'en lever"
                : null;
    }

    /** Purge les notes ponctuelles devenues sans objet. */
    @Transactional
    public void purgerPonctuelles() {
        notes.deleteByPorteeAndDateBefore(
                CoachNote.Portee.PONCTUELLE, LocalDate.now().minusWeeks(SEMAINES_RETENTION_PONCTUELLES));
    }
}
