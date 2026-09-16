package app.prepa.domain;

/**
 * Intention d'un cycle libre. Le type oriente la trame de seances que le coach construit ;
 * la formulation de l'athlete est conservee a part, en texte libre.
 */
public enum LigneDirectrice {
    /** Tenir le volume atteint en fin de prepa, sans nouvel objectif. */
    MAINTIEN_CHARGE,
    /** Travail de la puissance aerobie maximale. */
    VO2MAX,
    /** Volume facile, developpement du socle aerobie. */
    ENDURANCE_FONDAMENTALE,
    /** Denivele et terrain, orientation trail. */
    TRAIL_DENIVELE,
    /** Vitesse sur format court. */
    VITESSE_COURTE,
    /** Recuperation apres une course objectif. */
    REPRISE_POST_COURSE,
    /** Retour progressif apres blessure. */
    RETOUR_BLESSURE,
    AUTRE
}
