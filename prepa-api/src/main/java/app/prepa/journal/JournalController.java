package app.prepa.journal;

import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/athletes/{athleteId}/journal")
public class JournalController {

    private final JournalService journal;
    private final AthleteService athletes;

    public JournalController(JournalService journal, AthleteService athletes) {
        this.journal = journal;
        this.athletes = athletes;
    }

    @GetMapping
    public List<JournalDtos.EntryResponse> lister(@PathVariable UUID athleteId) {
        autoriser(athleteId);
        return journal.lister(athleteId).stream().map(JournalDtos.EntryResponse::from).toList();
    }

    @PutMapping
    public JournalDtos.EntryResponse enregistrer(
            @PathVariable UUID athleteId, @Valid @RequestBody JournalDtos.UpsertEntryRequest req) {
        autoriser(athleteId);
        return JournalDtos.EntryResponse.from(journal.enregistrer(athleteId, req));
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID athleteId, @PathVariable UUID entryId) {
        autoriser(athleteId);
        journal.supprimer(athleteId, entryId);
        return ResponseEntity.noContent().build();
    }

    private void autoriser(UUID athleteId) {
        athletes.modifiable(athleteId, CurrentPrincipal.get());
    }
}
