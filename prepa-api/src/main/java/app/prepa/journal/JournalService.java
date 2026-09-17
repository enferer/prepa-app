package app.prepa.journal;

import app.prepa.infra.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Journal de l'athlete : une entree par jour, creee ou mise a jour indifferemment. */
@Service
public class JournalService {

    /**
     * Mots qui signalent une gene. Leur presence leve le drapeau qui fera poser la question
     * au coach lors du point hebdomadaire.
     *
     * <p>C'est le texte qui decide, et non une case a cocher. Demander a quelqu'un qui vient
     * d'ecrire « le genou tire un peu depuis mardi » de cocher en plus une case, c'est lui
     * demander de declarer deux fois la meme chose — et l'oubli de la case suffisait a ce que
     * le coach ne voie rien.
     */
    private static final Pattern SIGNAUX_DE_DOULEUR = Pattern.compile(
            "douleur|douloureu|mal a|mal au|mal aux|g[eê]ne|g[eê]nant|blessure|blesse|"
                    + "tendinite|contracture|claquage|entorse|inflamm|tiraille|"
                    + "pincement|brûlure|brulure|boite|boiter"
                    // « ça tire », « le genou tire » : dans un journal d'entrainement, ce verbe
                    // ne decrit jamais autre chose qu'une gene.
                    + "|\\btire\\b|\\btirent\\b",
            Pattern.CASE_INSENSITIVE);

    private final JournalEntryRepository entrees;

    public JournalService(JournalEntryRepository entrees) {
        this.entrees = entrees;
    }

    /** Le texte mentionne-t-il une gene ? */
    public static boolean mentionneUneDouleur(String texte) {
        return texte != null && SIGNAUX_DE_DOULEUR.matcher(texte).find();
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
        // Le drapeau se deduit du texte ; il reste forcable pour un appel qui sait ce qu'il fait.
        entree.setDouleur(Boolean.TRUE.equals(req.douleur()) || mentionneUneDouleur(req.contenu()));
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
