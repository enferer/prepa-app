package app.prepa.admin;

import app.prepa.athlete.AthleteDtos;
import app.prepa.athlete.AthleteService;
import app.prepa.auth.ServiceKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration : creation des comptes et des cles de service. Reserve au role ADMIN. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AthleteService athleteService;
    private final ServiceKeyService serviceKeyService;

    public AdminController(AthleteService athleteService, ServiceKeyService serviceKeyService) {
        this.athleteService = athleteService;
        this.serviceKeyService = serviceKeyService;
    }

    @PostMapping("/athletes")
    public AthleteDtos.AthleteResponse creerAthlete(@Valid @RequestBody AthleteDtos.CreateAthleteRequest req) {
        return AthleteDtos.AthleteResponse.from(athleteService.creer(req));
    }

    @PatchMapping("/athletes/{athleteId}")
    public AthleteDtos.AthleteResponse majAthlete(
            @PathVariable UUID athleteId, @Valid @RequestBody AthleteDtos.UpdateAthleteRequest req) {
        return AthleteDtos.AthleteResponse.from(athleteService.renommer(athleteId, req));
    }

    @PostMapping("/service-keys")
    public ServiceKeyService.NouvelleCle creerCle(@Valid @RequestBody CreateServiceKeyRequest req) {
        return serviceKeyService.creer(req.nom(), java.util.Set.copyOf(req.scopes()), req.athleteId());
    }

    public record CreateServiceKeyRequest(
            @NotBlank String nom, @NotEmpty List<String> scopes, UUID athleteId) {}

}
