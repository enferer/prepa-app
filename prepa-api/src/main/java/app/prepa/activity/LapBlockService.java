package app.prepa.activity;

import app.prepa.domain.IntensiteTour;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Regroupement des tours en blocs d'effort.
 *
 * <p>Sur une seance a blocs — trois fois deux kilometres au seuil, par exemple — la montre
 * decoupe en tours de mille metres. Lus un par un, ces tours ne disent rien ; regroupes par
 * intensite, ils redonnent la seance telle qu'elle a ete concue, et c'est a ce niveau qu'on
 * juge son execution.
 *
 * <p>L'allure d'un bloc se recalcule sur ses totaux, et sa frequence cardiaque se pondere par
 * la duree de chaque tour : la moyenne des moyennes donnerait autant de poids a une
 * recuperation de trente secondes qu'a un effort de huit minutes.
 */
@Service
public class LapBlockService {

    public record Bloc(
            IntensiteTour intensite,
            int premierTour,
            int nbTours,
            int distanceM,
            int dureeSec,
            Integer allureSecKm,
            Integer fcMoy,
            Integer fcDebut,
            Integer fcFin,
            int denivelePosM,
            int deniveleNegM,
            List<Integer> alluresParTour) {

        /** Derive de frequence cardiaque au fil du bloc : un signe de fatigue ou de chaleur. */
        public Integer deriveFc() {
            return fcDebut == null || fcFin == null ? null : fcFin - fcDebut;
        }
    }

    /**
     * Une seance est structuree si ses tours melangent plusieurs intensites. Sur une sortie
     * en tours automatiques, tout se fondrait en un bloc unique : le regroupement n'aurait
     * alors rien a montrer, et la lecture reste kilometre par kilometre.
     */
    public boolean estStructuree(List<ActivityLap> tours) {
        Set<IntensiteTour> intensites = new HashSet<>();
        for (ActivityLap tour : tours) {
            intensites.add(tour.getIntensite() == null ? IntensiteTour.UNKNOWN : tour.getIntensite());
        }
        return intensites.size() > 1;
    }

    /** Regroupe les tours consecutifs de meme intensite. */
    public List<Bloc> grouper(List<ActivityLap> tours) {
        List<Bloc> blocs = new ArrayList<>();
        List<ActivityLap> courant = new ArrayList<>();
        IntensiteTour intensiteCourante = null;

        for (ActivityLap tour : tours) {
            IntensiteTour intensite = tour.getIntensite() == null ? IntensiteTour.UNKNOWN : tour.getIntensite();
            if (intensiteCourante != null && intensite != intensiteCourante) {
                blocs.add(construire(intensiteCourante, courant));
                courant = new ArrayList<>();
            }
            intensiteCourante = intensite;
            courant.add(tour);
        }
        if (!courant.isEmpty()) {
            blocs.add(construire(intensiteCourante, courant));
        }
        return blocs;
    }

    private Bloc construire(IntensiteTour intensite, List<ActivityLap> tours) {
        int distance = 0;
        int duree = 0;
        int denivelePos = 0;
        int deniveleNeg = 0;
        long fcPonderee = 0;
        int dureeAvecFc = 0;
        List<Integer> allures = new ArrayList<>();

        for (ActivityLap tour : tours) {
            distance += tour.getDistanceM() == null ? 0 : tour.getDistanceM();
            duree += tour.getDureeSec();
            denivelePos += tour.getDenivelePosM() == null ? 0 : tour.getDenivelePosM();
            deniveleNeg += tour.getDeniveleNegM() == null ? 0 : tour.getDeniveleNegM();
            if (tour.getFcMoy() != null) {
                fcPonderee += (long) tour.getFcMoy() * tour.getDureeSec();
                dureeAvecFc += tour.getDureeSec();
            }
            allures.add(tour.getAllureSecKm());
        }

        Integer allure = distance > 0 ? Math.round(duree / (distance / 1000f)) : null;
        Integer fcMoy = dureeAvecFc > 0 ? (int) (fcPonderee / dureeAvecFc) : null;

        return new Bloc(
                intensite,
                tours.getFirst().getIndexTour(),
                tours.size(),
                distance,
                duree,
                allure,
                fcMoy,
                tours.getFirst().getFcMoy() == null ? null : (int) tours.getFirst().getFcMoy(),
                tours.getLast().getFcMoy() == null ? null : (int) tours.getLast().getFcMoy(),
                denivelePos,
                deniveleNeg,
                allures);
    }
}
