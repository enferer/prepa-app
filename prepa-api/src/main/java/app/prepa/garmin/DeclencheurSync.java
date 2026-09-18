package app.prepa.garmin;

/** Ce qui a mis un passage en route. Sert a relire l'historique : qui a lance quoi, et pourquoi. */
public enum DeclencheurSync {

    /** Le passage complet periodique. */
    PLANIFIE,

    /** Une demande posee par un skill sur l'athlete, relevee au tour suivant. */
    DEMANDE,

    /** Un clic sur l'ecran d'exploitation, ou un appel direct. Le nom de l'auteur est conserve. */
    MANUEL
}
