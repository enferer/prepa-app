package app.prepa.domain;

/** Cycle de vie d'une seance planifiee. */
public enum StatutSeance {
    /** Pas encore echue. */
    A_VENIR,
    /** Realisee, conforme ou jugee acceptable par le coach. */
    VALIDEE,
    /** Non realisee. */
    MANQUEE,
    /** Reportee a une autre date, dans la meme semaine. */
    DEPLACEE,
    /** Retiree du plan : sacrifiee sans report. */
    ANNULEE;

    /** Une seance resolue compte dans l'assiduite ; une seance a venir non. */
    public boolean estResolue() {
        return this == VALIDEE || this == MANQUEE;
    }
}
