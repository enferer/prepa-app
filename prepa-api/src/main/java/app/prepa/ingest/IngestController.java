package app.prepa.ingest;

import app.prepa.activity.ActivityIngestService;
import app.prepa.athlete.Athlete;
import app.prepa.athlete.AthleteService;
import app.prepa.auth.CurrentPrincipal;
import app.prepa.auth.Principal;
import app.prepa.auth.ServiceKeyService;
import app.prepa.garmin.GarminCsvParser;
import app.prepa.garmin.GarminDtos;
import app.prepa.infra.ApiException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Entree des activites : import CSV manuel et lot normalise venu du worker Garmin.
 *
 * <p>Les deux voies convergent vers le meme service d'ingestion, donc vers les memes regles
 * de deduplication.
 */
@RestController
@RequestMapping("/api/v1/athletes/{athleteId}")
public class IngestController {

    private final AthleteService athletes;
    private final ActivityIngestService ingestion;
    private final GarminCsvParser parser;

    public IngestController(
            AthleteService athletes, ActivityIngestService ingestion, GarminCsvParser parser) {
        this.athletes = athletes;
        this.ingestion = ingestion;
        this.parser = parser;
    }

    /** Import d'un export CSV Garmin Connect. */
    @PostMapping("/activities/import-csv")
    public ImportCsvResponse importerCsv(@PathVariable UUID athleteId, @RequestParam("file") MultipartFile fichier) {
        Athlete athlete = cible(athleteId);
        if (fichier.isEmpty()) {
            throw ApiException.invalide("Fichier vide");
        }
        try {
            GarminCsvParser.Resultat lecture = parser.parser(fichier.getInputStream());
            GarminDtos.ResultatIngestion resultat = ingestion.ingerer(athlete, lecture.activites());
            return new ImportCsvResponse(resultat, lecture.avertissements());
        } catch (IOException e) {
            throw ApiException.invalide("Fichier illisible : " + e.getMessage());
        }
    }

    /** Lot d'activites deja normalisees, envoye par le worker Garmin. */
    @PostMapping("/activities/ingest")
    public GarminDtos.ResultatIngestion ingerer(
            @PathVariable UUID athleteId, @Valid @RequestBody List<GarminDtos.ActiviteBrute> lot) {
        return ingestion.ingerer(cible(athleteId), lot);
    }

    public record ImportCsvResponse(
            GarminDtos.ResultatIngestion resultat, List<GarminCsvParser.Avertissement> avertissements) {}

    /**
     * L'ingestion ecrit des donnees de terrain au nom d'un athlete : elle est reservee aux
     * clients machine portant le scope adequat, ou a l'athlete lui-meme pour un import manuel.
     */
    private Athlete cible(UUID athleteId) {
        Principal principal = CurrentPrincipal.get();
        if (principal.estCoach() && !principal.aLeScope(ServiceKeyService.SCOPE_INGEST)) {
            throw ApiException.forbidden("Cette cle de service n'a pas le droit d'ingerer des activites");
        }
        return athletes.accessible(athleteId, principal);
    }
}
