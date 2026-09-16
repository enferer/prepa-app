package app.prepa.coach;

import app.prepa.infra.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bilans hebdomadaires produits a la fin d'un point avec le coach. */
@Service
public class WeeklyReportService {

    private final WeeklyReportRepository rapports;

    public WeeklyReportService(WeeklyReportRepository rapports) {
        this.rapports = rapports;
    }

    /** Un seul bilan par semaine : refaire le point ecrase le precedent. */
    @Transactional
    public WeeklyReport enregistrer(UUID athleteId, UUID cycleId, CoachNoteDtos.CreateReportRequest req) {
        WeeklyReport rapport = rapports
                .findByCycleIdAndDateDebut(cycleId, req.dateDebut())
                .orElseGet(() -> new WeeklyReport(
                        UUID.randomUUID(), athleteId, cycleId, req.dateDebut(), req.bilan()));
        rapport.setBilan(req.bilan());
        rapport.setWeekId(req.weekId());
        rapport.setPointsAttention(req.pointsAttention());
        rapport.setChangements(req.changements());
        rapport.setConsignes(req.consignes());
        return rapports.save(rapport);
    }

    @Transactional(readOnly = true)
    public List<WeeklyReport> duCycle(UUID cycleId) {
        return rapports.findByCycleIdOrderByDateDebutDesc(cycleId);
    }

    @Transactional(readOnly = true)
    public WeeklyReport dernier(UUID athleteId) {
        List<WeeklyReport> derniers = rapports.findByAthleteIdOrderByDateDebutDesc(athleteId, Limit.of(1));
        return derniers.isEmpty() ? null : derniers.getFirst();
    }

    @Transactional(readOnly = true)
    public WeeklyReport parId(UUID id) {
        return rapports.findById(id).orElseThrow(() -> ApiException.notFound("Bilan"));
    }
}
