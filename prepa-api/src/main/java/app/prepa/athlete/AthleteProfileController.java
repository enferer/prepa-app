package app.prepa.athlete;

import app.prepa.auth.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

/**
 * Profil sportif : records, blessures, contraintes, seances signature.
 *
 * <p>Ce sont des faits que l'athlete connait mieux que quiconque — il les saisit lui-meme.
 */
@RestController
@RequestMapping("/api/v1/athletes/{athleteId}")
public class AthleteProfileController {

    private final AthleteProfileService profils;
    private final AthleteService athletes;

    public AthleteProfileController(AthleteProfileService profils, AthleteService athletes) {
        this.profils = profils;
        this.athletes = athletes;
    }

    @GetMapping("/profile")
    public ProfileDtos.ProfileResponse profil(@PathVariable UUID athleteId) {
        autoriser(athleteId);
        return ProfileDtos.ProfileResponse.from(profils.profil(athleteId));
    }

    @PutMapping("/profile")
    public ProfileDtos.ProfileResponse majProfil(
            @PathVariable UUID athleteId, @Valid @RequestBody ProfileDtos.UpdateProfileRequest req) {
        autoriser(athleteId);
        return ProfileDtos.ProfileResponse.from(profils.mettreAJour(athleteId, req));
    }

    @GetMapping("/records")
    public List<ProfileDtos.RecordResponse> records(@PathVariable UUID athleteId) {
        autoriser(athleteId);
        return profils.records(athleteId).stream().map(ProfileDtos.RecordResponse::from).toList();
    }

    @PostMapping("/records")
    public ProfileDtos.RecordResponse ajouterRecord(
            @PathVariable UUID athleteId, @Valid @RequestBody ProfileDtos.RecordRequest req) {
        autoriser(athleteId);
        return ProfileDtos.RecordResponse.from(profils.ajouterRecord(athleteId, req));
    }

    @DeleteMapping("/records/{recordId}")
    public ResponseEntity<Void> supprimerRecord(@PathVariable UUID athleteId, @PathVariable UUID recordId) {
        autoriser(athleteId);
        profils.supprimerRecord(athleteId, recordId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/injuries")
    public List<ProfileDtos.InjuryResponse> blessures(@PathVariable UUID athleteId) {
        autoriser(athleteId);
        return profils.blessures(athleteId).stream().map(ProfileDtos.InjuryResponse::from).toList();
    }

    @PostMapping("/injuries")
    public ProfileDtos.InjuryResponse ajouterBlessure(
            @PathVariable UUID athleteId, @Valid @RequestBody ProfileDtos.InjuryRequest req) {
        autoriser(athleteId);
        return ProfileDtos.InjuryResponse.from(profils.ajouterBlessure(athleteId, req));
    }

    @PatchMapping("/injuries/{injuryId}")
    public ProfileDtos.InjuryResponse modifierBlessure(
            @PathVariable UUID athleteId,
            @PathVariable UUID injuryId,
            @Valid @RequestBody ProfileDtos.InjuryRequest req) {
        autoriser(athleteId);
        return ProfileDtos.InjuryResponse.from(profils.modifierBlessure(athleteId, injuryId, req));
    }

    @GetMapping("/constraints")
    public List<ProfileDtos.ConstraintResponse> contraintes(@PathVariable UUID athleteId) {
        autoriser(athleteId);
        return profils.contraintes(athleteId).stream().map(ProfileDtos.ConstraintResponse::from).toList();
    }

    @PostMapping("/constraints")
    public ProfileDtos.ConstraintResponse ajouterContrainte(
            @PathVariable UUID athleteId, @Valid @RequestBody ProfileDtos.ConstraintRequest req) {
        autoriser(athleteId);
        return ProfileDtos.ConstraintResponse.from(profils.ajouterContrainte(athleteId, req));
    }

    @DeleteMapping("/constraints/{constraintId}")
    public ResponseEntity<Void> supprimerContrainte(
            @PathVariable UUID athleteId, @PathVariable UUID constraintId) {
        autoriser(athleteId);
        profils.supprimerContrainte(athleteId, constraintId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/signature-sessions")
    public List<ProfileDtos.SignatureSessionResponse> signatures(@PathVariable UUID athleteId) {
        autoriser(athleteId);
        return profils.seancesSignature(athleteId).stream()
                .map(ProfileDtos.SignatureSessionResponse::from)
                .toList();
    }

    @PostMapping("/signature-sessions")
    public ProfileDtos.SignatureSessionResponse ajouterSignature(
            @PathVariable UUID athleteId, @Valid @RequestBody ProfileDtos.SignatureSessionRequest req) {
        autoriser(athleteId);
        return ProfileDtos.SignatureSessionResponse.from(profils.ajouterSignature(athleteId, req));
    }

    @PatchMapping("/signature-sessions/{id}")
    public ProfileDtos.SignatureSessionResponse modifierSignature(
            @PathVariable UUID athleteId,
            @PathVariable UUID id,
            @Valid @RequestBody ProfileDtos.SignatureSessionRequest req) {
        autoriser(athleteId);
        return ProfileDtos.SignatureSessionResponse.from(profils.modifierSignature(athleteId, id, req));
    }

    private void autoriser(UUID athleteId) {
        athletes.accessible(athleteId, CurrentPrincipal.get());
    }
}
