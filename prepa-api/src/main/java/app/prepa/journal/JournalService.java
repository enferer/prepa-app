package app.prepa.journal;

import app.prepa.infra.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Journal de l'athlete : une entree par jour, creee ou mise a jour indifferemment. */
@Service
public class JournalService {

    private final JournalEntryRepository entrees;

    public JournalService(JournalEntryRepository entrees) {
        this.entrees = entrees;
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> lister(UUID athleteId) {
        return entrees.findByAthleteIdOrderByDateDesc(athleteId);
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> dernieres(UUID athleteId, int combien) {
        return entrees.findByAthleteIdOrderByDateDesc(athleteId, Limit.of(combien));
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> entre(UUID athleteId, LocalDate debut, LocalDate fin) {
        return entrees.findByAthleteIdAndDateBetweenOrderByDateAsc(athleteId, debut, fin);
    }

    /** Une seule entree par jour : reecrire la meme date met a jour plutot que de dupliquer. */
    @Transactional
    public JournalEntry enregistrer(UUID athleteId, JournalDtos.UpsertEntryRequest req) {
        JournalEntry entree = entrees
                .findByAthleteIdAndDate(athleteId, req.date())
                .orElseGet(() -> new JournalEntry(UUID.randomUUID(), athleteId, req.date(), req.contenu()));
        entree.setContenu(req.contenu());
        entree.setHumeur(req.humeur());
        entree.setFatigue(req.fatigue());
        entree.setSommeilH(req.sommeilH());
        entree.setDouleur(Boolean.TRUE.equals(req.douleur()));
        return entrees.save(entree);
    }

    @Transactional
    public void supprimer(UUID athleteId, UUID entryId) {
        JournalEntry entree = entrees.findById(entryId).orElseThrow(() -> ApiException.notFound("Entree"));
        if (!entree.getAthleteId().equals(athleteId)) {
            throw ApiException.notFound("Entree");
        }
        entrees.delete(entree);
    }
}
