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
     *
     * <p>Le relief est neutralise de la meme facon : une sortie vallonnee est ramenee a son
     * allure corrigee de la pente quand la montre l'a calculee, et ecartee sinon. Sans cela,
     * deux trails suffisent a faire croire qu'un athlete court son endurance trop lentement.
     *
     * @param nbEcartees sorties laissees de cote faute de pouvoir corriger leur relief
     * @param surAllureCorrigee au moins une sortie retenue l'a ete par son allure corrigee
     */
    public record AllureParType(
            String type,
            String libelle,
            int nbSeances,
            Integer allureReelleSecKm,
            Integer allureCibleSecKm,
            Integer ecartSecKm,
            boolean surLesBlocsDEffort,
            int nbEcartees,
            boolean surAllureCorrigee) {}

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
     * D'ou vient un meilleur temps, et donc quelle confiance lui accorder.
     *
     * <p>La distinction precedente — « sur les tours » ou non — ne disait pas ce qu'elle avait
     * l'air de dire : elle refletait seulement si la montre avait transmis le detail des tours.
     * Un dix kilometres couru pour lui-meme etait annonce « estime » parce qu'il tenait en un
     * seul tour, tandis qu'un kilometre devale en descente au milieu d'un trail passait pour
     * « mesure ». Ces trois provenances disent la vraie nature du chiffre.
     */
    public enum Provenance {
        /** La sortie fait la distance : le chrono est celui de la course elle-meme. */
        SORTIE,
        /** Meilleur segment retrouve en faisant glisser une fenetre sur les tours. */
        TOURS,
        /** Extrapole de l'allure moyenne d'une sortie plus longue — un ordre de grandeur. */
        ESTIMATION
    }

    /**
     * Meilleur temps sur une distance.
     *
     * <p>Une estimation ne peut pas battre une mesure : elle n'est retenue que si rien de mieux
     * n'existe. Annoncer un record tire d'une moyenne devant un chrono reellement couru
     * reviendrait a preferer le calcul a la realite.
     */
    public record RecordEstime(
            int distanceM,
            Integer tempsSec,
            Integer allureSecKm,
            LocalDate date,
            UUID activityId,
            Provenance provenance,
            /** Denivele descendant du segment retenu, quand il explique le chrono. */
            Integer deniveleNetM) {}

    public record TendanceFc(Integer fcMoyRecente, Integer fcMoyPrecedente, Integer ecart, String lecture) {}

    /** Comparaison d'une periode a l'autre, pour situer le cycle en cours. */
    public record Comparaison(Periode avant, Periode pendant, List<Delta> deltas) {}

    public record Periode(String libelle, LocalDate debut, LocalDate fin, VolumeResume volume, Integer allureMoySecKm,
            Integer fcMoy) {}

    public record Delta(String mesure, Double avant, Double pendant, Double variationPct, boolean amelioration) {}
}
