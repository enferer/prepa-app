package app.prepa.domain;

/**
 * Cycle de vie d'une seance planifiee, en trois temps.
 *
 * <p>La distinction tient a qui pose le statut. {@link #REALISEE} et {@link #NON_REALISEE}
 * sont des <em>constats</em>, poses par le systeme des qu'une activite correspond a la seance
 * ou que sa date passe sans que rien ne vienne : l'athlete voit sa sortie comptee le soir
 * meme, sans attendre le point hebdomadaire. {@link #ANALYSEE} est un <em>jugement</em>, pose
 * par le coach apres avoir regarde l'execution et, s'il y a lieu, demande la cause d'un ecart.
 *
 * <p>C'est pourquoi il n'existe pas de statut « manquee » automatique : declarer une seance
 * manquee avant d'avoir demande a l'athlete ce qui s'est passe reviendrait a juger sans
 * savoir, ce que la methodologie interdit. Le constat dit qu'il ne s'est rien passe ; la
 * raison vit dans le commentaire du coach.
 */
public enum StatutSeance {

    /** Prevue, pas encore echue. */
    A_VENIR,

    /** Une activite correspond a cette seance. Constat automatique. */
    REALISEE,

    /** La date est passee et rien n'est venu. Constat automatique, pas un reproche. */
    NON_REALISEE,

    /** Passee en revue par le coach, qui l'a commentee. */
    ANALYSEE,

    /** Reportee a une autre date, dans la meme semaine. */
    DEPLACEE,

    /** Retiree du plan : sacrifiee sans report. */
    ANNULEE;

    /**
     * Le sort de la seance est fixe : elle a eu lieu ou non.
     * C'est sur ces seances-la que se mesure l'assiduite — une seance a venir n'est ni tenue
     * ni manquee.
     */
    public boolean estTranchee() {
        return this == REALISEE || this == NON_REALISEE || this == ANALYSEE;
    }

    /** La seance a bien eu lieu, qu'elle ait deja ete commentee ou non. */
    public boolean aEuLieu() {
        return this == REALISEE || this == ANALYSEE;
    }

    /** Reste-t-il quelque chose a regarder pour le coach ? */
    public boolean attendLeCoach() {
        return this == REALISEE || this == NON_REALISEE;
    }

    /** Un constat automatique ne doit jamais ecraser une decision deja prise par le coach. */
    public boolean accepteUnConstatAutomatique() {
        return this == A_VENIR || this == REALISEE || this == NON_REALISEE;
    }
}
