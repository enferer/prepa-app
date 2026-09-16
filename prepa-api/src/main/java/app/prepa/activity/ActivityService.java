package app.prepa.activity;

import app.prepa.infra.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultation des activites realisees. */
@Service
public class ActivityService {

    private final ActivityRepository activities;
    private final AnalysisStateRepository etats;
    private final LapBlockService blocs;

    public ActivityService(
            ActivityRepository activities, AnalysisStateRepository etats, LapBlockService blocs) {
        this.activities = activities;
        this.etats = etats;
        this.blocs = blocs;
    }

    @Transactional(readOnly = true)
    public List<Activity> lister(UUID athleteId) {
        return activities.findByAthleteIdOrderByStartedAtDesc(athleteId);
    }

    @Transactional(readOnly = true)
    public List<Activity> entre(UUID athleteId, LocalDate debut, LocalDate fin) {
        return activities.entre(athleteId, debut, fin);
    }

    @Transactional(readOnly = true)
    public Activity parId(UUID activityId) {
        return activities.findById(activityId).orElseThrow(() -> ApiException.notFound("Activite"));
    }

    @Transactional(readOnly = true)
    public ActivityDtos.ActivityDetail detail(UUID activityId) {
        Activity a = parId(activityId);
        List<ActivityLap> tours = a.getTours();
        boolean structuree = blocs.estStructuree(tours);

        return new ActivityDtos.ActivityDetail(
                ActivityDtos.ActivityResume.from(a),
                a.getDureeMouvementSec(),
                a.getMeilleureAllureSecKm(),
                a.getGapMoySecKm(),
                a.getFcMax(),
                a.getFcMin(),
                a.getCadenceMoy(),
                a.getDeniveleNegM(),
                a.getAltitudeMinM(),
                a.getAltitudeMaxM(),
                a.getCalories(),
                a.getTeAerobie(),
                a.getTeAnaerobie(),
                a.getTeLabel(),
                a.getChargeEntrainement(),
                a.getVo2max(),
                a.getLieu(),
                a.getRessenti(),
                a.getMeteo(),
                a.getZonesFc(),
                tours.stream().map(ActivityDtos.TourResponse::from).toList(),
                structuree,
                structuree ? blocs.grouper(tours) : List.of());
    }

    /** Ressenti et effort percu : ce que la montre ne mesure pas. */
    @Transactional
    public Activity enregistrerRessenti(UUID activityId, ActivityDtos.FeedbackRequest req) {
        Activity activite = parId(activityId);
        if (req.rpe() != null) {
            activite.setRpe(req.rpe());
        }
        if (req.ressenti() != null) {
            activite.setRessenti(req.ressenti());
        }
        return activities.save(activite);
    }

    /** Activites que le coach n'a pas encore passees en revue. */
    @Transactional(readOnly = true)
    public List<Activity> nonAnalysees(UUID athleteId) {
        return activities.nonAnalysees(athleteId);
    }

    @Transactional
    public int marquerAnalysees(UUID athleteId, List<UUID> activityIds) {
        int marquees = 0;
        for (UUID id : activityIds) {
            Activity activite = parId(id);
            if (!activite.getAthleteId().equals(athleteId)) {
                throw ApiException.notFound("Activite");
            }
            if (!etats.existsById(id)) {
                etats.save(new AnalysisState(id, athleteId));
                marquees++;
            }
        }
        return marquees;
    }
}
