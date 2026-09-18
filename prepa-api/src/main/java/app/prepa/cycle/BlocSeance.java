package app.prepa.cycle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Un bloc de seance, tel que le coach le pose.
 *
 * <p>C'est la forme <em>saisie</em> du deroule, a ne pas confondre avec
 * {@link StructureSeance.BlocPrevu}, qui est la forme <em>affichee</em> : celle-ci ne porte que
 * les faits, celle-la y ajoute ce qui s'en deduit — la duree du bloc et son libelle.
 *
 * <p>Le partage est deliberé : ce que le coach ecrit doit rester le strict necessaire. Lui
 * faire saisir « 3×1 km à 5:41 » en plus de {@code repetitions: 3, distanceKm: 1,
 * allureSecKm: 341} inviterait les deux a diverger.
 *
 * @param role ce que le bloc fait dans la seance
 * @param repetitions nombre de fois que le bloc revient ; un par defaut
 * @param distanceKm longueur d'une repetition
 * @param dureeSec duree d'une repetition, quand elle se compte en temps plutot qu'en distance
 * @param allureSecKm allure a tenir
 * @param recupSec recuperation entre deux repetitions
 */
public record BlocSeance(
        @NotNull StructureSeance.RoleBloc role,
        @Min(1) @Max(40) Integer repetitions,
        @Positive BigDecimal distanceKm,
        @Positive Integer dureeSec,
        @Positive Integer allureSecKm,
        @PositiveOrZero Integer recupSec) {

    public BlocSeance {
        if (repetitions == null) {
            repetitions = 1;
        }
    }

    /** Un bloc qui ne dit ni sa longueur ni sa duree ne se dessine pas, et ne veut rien dire. */
    public boolean estMesurable() {
        return distanceKm != null || dureeSec != null;
    }
}
