package app.prepa.analysis;

import app.prepa.domain.TypeActivite;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de la synthese d'entrainement. */
public final class AnalysisDtos {

    private AnalysisDtos() {}

    /** Synthese complete, equivalent de l'ancienne sortie texte de l'analyse. */
    public record Synthese(
            Fenetre fenetre,
            List<SemaineVolume> volumeHebdo,
            VolumeResume volume,
            AllureEf allureEf,
            List<EffortNotable> effortsRapides,
            List<EffortNotable> plusLonguesSorties,
            EffortNotable plusLongueDeToujours,
            List<RecordEstime> records,
            TendanceFc tendanceFc,
            Comparaison avantPendantCycle,
            List<AllureParType> alluresParType,
            RepartitionIntensite repartition) {}

    /**
     * Allure reellement tenue sur un type de seance, face a l'allure visee.
     *
     * <p>C'est la mesure qui dit si l'entrainement fait ce qu'il pretend faire. Sur une seance
     * a intervalles, l'allure retenue est celle des blocs d'effort et non la moyenne de la
     * sortie : cette derniere melange l'echauffement, les recuperations et le retour au calme,
     * et ne se compare a aucune cible.
     */
    public record AllureParType(
            String type,
            String libelle,
            int nbSeances,
            Integer allureReelleSecKm,
            Integer allureCibleSecKm,
            Integer ecartSecKm,
            boolean surLesBlocsDEffort) {}

    /**
     * Part du volume couru facile, face a la part couru en intensite.
     *
     * <p>La methodologie demande environ quatre cinquiemes du volume en endurance. C'est le
     * principe le plus simple a enoncer et le plus souvent trahi : on court son facile trop
     * vite et son rapide trop lentement. Le rendre visible permet a l'athlete de le verifier
     * lui-meme.
     */
    public record RepartitionIntensite(
            double kmFacile,
            double kmIntensite,
            int partFacilePct,
            int partIntensitePct,
            String lecture) {}

    public record Fenetre(LocalDate debut, LocalDate fin, int nbCourses, String libelle) {}

    public record SemaineVolume(
            LocalDate lundi, int anneeIso, int numeroIso, double km, int nbCourses, Integer deniveleM) {}

    public record VolumeResume(
            int nbCourses,
            double kmTotal,
            int semainesActives,
            double kmParSemaineMoyen,
            double kmParSemaineMin,
            double kmParSemaineMax,
            double seancesParSemaine) {}

    /**
     * Allure d'endurance fondamentale reellement tenue.
     * Le seuil de frequence cardiaque retenu est relatif a la FC max quand elle est connue.
     */
    public record AllureEf(Integer allureMoySecKm, int nbSeances, int seuilFcRetenu, boolean seuilRelatif) {}

    public record EffortNotable(
            UUID activityId,
            LocalDate date,
            TypeActivite type,
            String titre,
            double distanceKm,
            Integer allureSecKm,
            Short fcMoy,
            Integer denivelePosM) {}

    /**
     * Meilleur temps estime sur une distance, calcule par fenetre glissante sur les tours.
     * Sans tours, l'estimation retombe sur l'allure moyenne de la sortie — c'est alors une
     * approximation, signalee comme telle.
     */
    public record RecordEstime(
            int distanceM,
            Integer tempsSec,
            Integer allureSecKm,
            LocalDate date,
            UUID activityId,
            boolean surTours) {}

    public record TendanceFc(Integer fcMoyRecente, Integer fcMoyPrecedente, Integer ecart, String lecture) {}

    /** Comparaison d'une periode a l'autre, pour situer le cycle en cours. */
    public record Comparaison(Periode avant, Periode pendant, List<Delta> deltas) {}

    public record Periode(String libelle, LocalDate debut, LocalDate fin, VolumeResume volume, Integer allureMoySecKm,
            Integer fcMoy) {}

    public record Delta(String mesure, Double avant, Double pendant, Double variationPct, boolean amelioration) {}
}
