package app.prepa.domain;

/** Phases d'une preparation, au sens de la periodisation du coach. */
public enum BlocEntrainement {
    BASE,
    DEVELOPPEMENT,
    SPECIFIQUE,
    AFFUTAGE,
    DECHARGE,
    /** Semaine d'un cycle libre, hors periodisation de course. */
    LIBRE,
    REPRISE
}
