package app.prepa.cycle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ce qu'il y a a faire dans une seance, sous une forme dessinable.
 *
 * <p>Le coach ecrit ses seances en francais — « 2 km ech. + 3x1 km a AM (5:41) recup 2 min +
 * 2 km RAC ». C'est la bonne forme pour etre lu, la pire pour etre vu : l'athlete doit
 * reconstruire mentalement le deroule a chaque fois. Cette classe relit cette phrase et en
 * sort la suite de blocs qu'elle decrit, pour que l'application puisse la tracer.
 *
 * <p>La lecture se fait ici plutot que dans l'application pour une raison simple : c'est une
 * connaissance du metier — savoir que « RAC » est un retour au calme et que « 3x1 km » est un
 * bloc d'effort repete — et elle se teste. Une erreur de lecture doit casser un test, pas
 * s'afficher de travers chez l'athlete.
 *
 * <p>Rien n'est invente : quand la phrase ne se laisse pas lire, on retombe sur un bloc unique
 * tire du type et de la distance cible. Un dessin faux serait pire que pas de dessin.
 *
 * <p>Cette relecture n'est qu'un <em>repli</em>. Depuis que {@link PlannedSession#getStructure()}
 * existe, le coach pose ses blocs directement et il n'y a plus rien a deviner ; le parser reste
 * pour les seances ecrites avant, qu'il aurait ete inutilement couteux de reprendre.
 */
public final class StructureSeance {

    private StructureSeance() {}

    /** Ce que fait un bloc, et donc la couleur qu'il portera. */
    public enum RoleBloc {
        ECHAUFFEMENT,
        ENDURANCE,
        EFFORT,
        LIGNES,
        RECUPERATION,
        RETOUR_AU_CALME
    }

    /**
     * Un morceau de la seance.
     *
     * <p>{@code dureeEstimeeSec} couvre les repetitions et les recuperations qui les separent :
     * c'est cette grandeur qui donne sa largeur au bloc sur le dessin, parce qu'une seance se
     * vit en temps, pas en kilometres. Elle reste une estimation — l'allure reelle du jour
     * n'est pas connue au moment ou la seance est prevue.
     */
    public record BlocPrevu(
            RoleBloc role,
            int repetitions,
            BigDecimal distanceKm,
            Integer dureeSec,
            Integer allureSecKm,
            Integer recupSec,
            int dureeEstimeeSec,
            String libelle) {}

    /** A defaut d'allure connue, une seance se court a six minutes au kilometre. */
    private static final int ALLURE_PAR_DEFAUT = 360;

    private static final Pattern ALLURE = Pattern.compile("(\\d{1,2}):([0-5]\\d)");
    private static final Pattern REPETITIONS = Pattern.compile("(\\d{1,2})\\s*[x×*]\\s*");
    private static final Pattern REPETITIONS_MOT =
            Pattern.compile("(\\d{1,2})\\s+(?:accelerations?|lignes?|fractions?|repetitions?|fois)\\b");
    private static final Pattern DISTANCE_KM = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*km\\b");
    private static final Pattern DISTANCE_M = Pattern.compile("(\\d{2,5})\\s*m\\b");
    private static final Pattern DUREE_MIN = Pattern.compile("(\\d{1,3})\\s*(?:min|')\\b");
    private static final Pattern DUREE_SEC = Pattern.compile("(\\d{1,3})\\s*(?:s|\")\\b");
    private static final Pattern RECUP =
            Pattern.compile("recup\\w*\\s*(?:de\\s*)?(?:(\\d{1,2}):([0-5]\\d)|(\\d+(?:[.,]\\d+)?)\\s*(min|s|km)\\b)");

    /**
     * L'annonce du total qui ouvre une sortie longue : « SL 24 km : 9 km EF + ... ». Elle
     * redit ce que porte deja la distance cible, et la laisser passer ferait du premier bloc
     * une sortie de vingt-quatre kilometres suivie de ses propres morceaux.
     */
    private static final Pattern ENTETE_TOTAL =
            Pattern.compile("^\\s*(?:sl|ef|am|seuil|vma|cotes)?\\s*\\d+(?:[.,]\\d+)?\\s*km\\s*:\\s*");

    /**
     * La suite des blocs d'une seance, du depart a l'arrivee.
     *
     * <p>Vide pour ce qui ne se court pas : un repos n'a pas de deroule, un renforcement non
     * plus — son contenu vit dans son focus.
     */
    public static List<BlocPrevu> deduire(PlannedSession seance) {
        if (!seance.estCourseAPied()) {
            return List.of();
        }
        Map<String, Integer> allures = legende(seance.getAlluresTexte());

        // Ce que le coach a pose l'emporte toujours. Relire sa phrase alors qu'il a ecrit les
        // blocs reviendrait a deviner ce qu'on nous a dit.
        if (seance.getStructure() != null && !seance.getStructure().isEmpty()) {
            return depuisLaSaisie(seance.getStructure(), allures);
        }

        List<BlocPrevu> lus = lire(seance.getDescription(), allures);

        // Un bloc unique sans repetition n'apprend rien de plus que le titre : on prefere alors
        // la forme sure, batie sur la distance cible, qui au moins ne se trompe pas.
        boolean exploitable = lus.size() >= 2 || lus.stream().anyMatch(b -> b.repetitions() > 1);
        return exploitable ? lus : blocUnique(seance, allures);
    }

    /**
     * Le deroule tel que le coach l'a pose, complete de ce qui s'en deduit.
     *
     * <p>Il n'ecrit que les faits — un role, un nombre de repetitions, une longueur, une allure.
     * La duree du bloc et son libelle se calculent ici, une fois, plutot que d'etre saisis deux
     * fois et de finir par se contredire.
     */
    private static List<BlocPrevu> depuisLaSaisie(List<BlocSeance> blocs, Map<String, Integer> legende) {
        List<BlocPrevu> prevus = new ArrayList<>();
        for (BlocSeance bloc : blocs) {
            if (!bloc.estMesurable()) {
                continue;
            }
            Integer allureSecKm = bloc.allureSecKm();
            if (allureSecKm == null && bloc.role() != RoleBloc.EFFORT) {
                allureSecKm = legende.values().stream().max(Integer::compareTo).orElse(null);
            }
            int recupSec = bloc.recupSec() == null ? 0 : bloc.recupSec();
            int parRepetition = bloc.distanceKm() != null
                    ? bloc.distanceKm()
                            .multiply(BigDecimal.valueOf(allureSecKm == null ? ALLURE_PAR_DEFAUT : allureSecKm))
                            .intValue()
                    : bloc.dureeSec();
            int total = bloc.repetitions() * parRepetition + (bloc.repetitions() - 1) * recupSec;
            prevus.add(new BlocPrevu(
                    bloc.role(),
                    bloc.repetitions(),
                    bloc.distanceKm(),
                    bloc.dureeSec(),
                    allureSecKm,
                    bloc.recupSec(),
                    Math.max(30, total),
                    libelle(bloc.repetitions(), bloc.distanceKm(), bloc.dureeSec(), allureSecKm, bloc.role())));
        }
        return prevus;
    }

    // --- La legende : « AM 5:41 · ech/RAC 6:35 » ---------------------------------------------

    /**
     * Les allures nommees de la seance, telles que le coach les a listees.
     *
     * <p>{@code alluresTexte} est deja la legende du dessin : elle dit quelle allure porte quel
     * nom. Il n'y a donc pas besoin d'aller chercher les zones du cycle — la seance se suffit.
     */
    private static Map<String, Integer> legende(String alluresTexte) {
        Map<String, Integer> legende = new LinkedHashMap<>();
        if (alluresTexte == null || alluresTexte.isBlank()) {
            return legende;
        }
        for (String segment : normaliser(alluresTexte).split("[·|,;]")) {
            Matcher allure = ALLURE.matcher(segment);
            if (!allure.find()) {
                continue;
            }
            int secKm = Integer.parseInt(allure.group(1)) * 60 + Integer.parseInt(allure.group(2));
            legende.putIfAbsent("", secKm);
            for (String mot : segment.substring(0, allure.start()).split("[\\s/]+")) {
                String cle = mot.replaceAll("[^a-z]", "");
                if (cle.length() >= 2) {
                    legende.putIfAbsent(cle, secKm);
                }
            }
        }
        return legende;
    }

    /**
     * L'allure qui correspond a un morceau de phrase : celle qu'il cite, celle qu'il nomme.
     *
     * <p>Entre plusieurs allures nommees, c'est la premiere de la phrase qui l'emporte, pas la
     * premiere de la legende. Dans « 2x4 km a AM recup 2 km EF », l'allure du bloc est celle de
     * l'effort qu'il annonce ; celle qui suit « recup » decrit ce qui separe les repetitions.
     */
    private static Integer allureDe(String segment, Map<String, Integer> legende) {
        Matcher citee = ALLURE.matcher(segment);
        if (citee.find()) {
            return Integer.parseInt(citee.group(1)) * 60 + Integer.parseInt(citee.group(2));
        }
        if (segment.contains("allure marathon") && legende.containsKey("am")) {
            return legende.get("am");
        }
        Integer retenue = null;
        int plusTot = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> nommee : legende.entrySet()) {
            String cle = nommee.getKey();
            if (cle.isEmpty()) {
                continue;
            }
            Matcher place = Pattern.compile("\\b" + cle + "\\b").matcher(segment);
            if (place.find() && place.start() < plusTot) {
                plusTot = place.start();
                retenue = nommee.getValue();
            }
        }
        return retenue;
    }

    // --- La phrase ----------------------------------------------------------------------------

    private static List<BlocPrevu> lire(String description, Map<String, Integer> legende) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        String phrase = ENTETE_TOTAL.matcher(normaliser(description)).replaceFirst("");
        List<BlocPrevu> blocs = new ArrayList<>();
        for (String segment : phrase.split("\\s*(?:\\+|,? puis |\\bpuis\\b)\\s*")) {
            BlocPrevu bloc = bloc(segment, legende);
            if (bloc != null) {
                blocs.add(bloc);
            }
        }
        return blocs;
    }

    private static BlocPrevu bloc(String segment, Map<String, Integer> legende) {
        int repetitions = repetitions(segment);
        BigDecimal distanceKm = distanceKm(segment);
        Integer dureeSec = dureeSec(segment, distanceKm != null);
        if (distanceKm == null && dureeSec == null) {
            return null;
        }

        Integer allureSecKm = allureDe(segment, legende);
        Integer recupSec = repetitions > 1 ? recupSec(segment) : null;
        RoleBloc role = role(segment, repetitions, allureSecKm, dureeSec, legende);
        if (allureSecKm == null && role != RoleBloc.EFFORT) {
            // Faute de mieux, la plus lente des allures annoncees. Prendre la premiere de la
            // liste ferait courir l'echauffement a l'allure des fractions ; prendre la plus
            // lente se trompe au pire d'un footing, jamais d'un contresens.
            allureSecKm = legende.values().stream().max(Integer::compareTo).orElse(null);
        }

        int parRepetition = distanceKm != null && allureSecKm != null
                ? distanceKm.multiply(BigDecimal.valueOf(allureSecKm)).intValue()
                : dureeSec != null
                        ? dureeSec
                        : distanceKm.multiply(BigDecimal.valueOf(ALLURE_PAR_DEFAUT)).intValue();
        int total = repetitions * parRepetition + (repetitions - 1) * (recupSec == null ? 0 : recupSec);

        return new BlocPrevu(
                role, repetitions, distanceKm, dureeSec, allureSecKm, recupSec,
                Math.max(30, total), libelle(repetitions, distanceKm, dureeSec, allureSecKm, role));
    }

    private static int repetitions(String segment) {
        Matcher fois = REPETITIONS.matcher(segment);
        if (fois.find()) {
            return Integer.parseInt(fois.group(1));
        }
        Matcher nommees = REPETITIONS_MOT.matcher(segment);
        return nommees.find() ? Integer.parseInt(nommees.group(1)) : 1;
    }

    private static BigDecimal distanceKm(String segment) {
        Matcher enKm = DISTANCE_KM.matcher(segment);
        if (enKm.find()) {
            return new BigDecimal(enKm.group(1).replace(',', '.'));
        }
        Matcher enM = DISTANCE_M.matcher(segment);
        if (enM.find()) {
            return new BigDecimal(enM.group(1)).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
        }
        return null;
    }

    /**
     * Une duree n'est retenue que faute de distance : « 3x1 km recup 2 min » dure ce que dure
     * le kilometre, pas deux minutes.
     */
    private static Integer dureeSec(String segment, boolean aUneDistance) {
        if (aUneDistance) {
            return null;
        }
        Matcher minutes = DUREE_MIN.matcher(segment);
        if (minutes.find()) {
            return Integer.parseInt(minutes.group(1)) * 60;
        }
        Matcher secondes = DUREE_SEC.matcher(segment);
        return secondes.find() ? Integer.parseInt(secondes.group(1)) : null;
    }

    private static Integer recupSec(String segment) {
        Matcher recup = RECUP.matcher(segment);
        if (!recup.find()) {
            return null;
        }
        if (recup.group(1) != null) {
            // « recup 2:30 » se lit comme une duree, pas comme une allure.
            return Integer.parseInt(recup.group(1)) * 60 + Integer.parseInt(recup.group(2));
        }
        double valeur = Double.parseDouble(recup.group(3).replace(',', '.'));
        // Une recuperation comptee en kilometres se court en trottinant : six minutes au
        // kilometre est l'ordre de grandeur, et seule sa largeur sur le dessin en depend.
        return switch (recup.group(4)) {
            case "min" -> (int) Math.round(valeur * 60);
            case "km" -> (int) Math.round(valeur * ALLURE_PAR_DEFAUT);
            default -> (int) Math.round(valeur);
        };
    }

    /**
     * Ce que le bloc fait dans la seance.
     *
     * <p>L'ordre des tests porte la lecture. Il place volontairement les repetitions apres les
     * mots-cles : « 3x1 km a AM recup 2 min » contient le mot « recup » sans etre une
     * recuperation, et « 4x20 s accelerations » se repete sans etre un bloc d'effort. Faute de
     * tout indice, un bloc est de l'endurance — c'est le cas le plus frequent et le moins
     * couteux a se tromper.
     */
    private static RoleBloc role(
            String segment,
            int repetitions,
            Integer allureSecKm,
            Integer dureeSec,
            Map<String, Integer> legende) {
        if (segment.contains("ligne") || segment.contains("acceleration") || segment.contains("sprint")) {
            return RoleBloc.LIGNES;
        }
        if (segment.matches(".*\\bech\\w*\\b.*")) {
            return RoleBloc.ECHAUFFEMENT;
        }
        if (segment.matches(".*\\brac\\b.*") || segment.contains("retour au calme")) {
            return RoleBloc.RETOUR_AU_CALME;
        }
        // « 6 km EF + 5x20 s » : des repetitions tres courtes et sans allure annoncee sont des
        // lignes droites, pas un bloc de qualite. Les compter comme un effort ferait passer un
        // footing pour une seance dure.
        if (repetitions > 1 && allureSecKm == null && dureeSec != null && dureeSec <= 45) {
            return RoleBloc.LIGNES;
        }
        if (repetitions > 1) {
            return RoleBloc.EFFORT;
        }
        if (segment.stripLeading().startsWith("recup")) {
            return RoleBloc.RECUPERATION;
        }
        // Une allure nettement plus rapide que la plus lente de la legende est un effort, meme
        // isolee : « 8 km EF + 4 km a AM » est une sortie longue avec un bloc dedans.
        if (allureSecKm != null && !legende.isEmpty()) {
            int laPlusLente = legende.values().stream().mapToInt(Integer::intValue).max().orElse(allureSecKm);
            if (laPlusLente - allureSecKm >= 15) {
                return RoleBloc.EFFORT;
            }
        }
        return RoleBloc.ENDURANCE;
    }

    private static String libelle(
            int repetitions, BigDecimal distanceKm, Integer dureeSec, Integer allureSecKm, RoleBloc role) {
        StringBuilder texte = new StringBuilder();
        if (repetitions > 1) {
            texte.append(repetitions).append('×');
        }
        if (distanceKm != null) {
            texte.append(distanceKm.compareTo(BigDecimal.ONE) < 0
                    ? distanceKm.multiply(BigDecimal.valueOf(1000)).intValue() + " m"
                    : distanceKm.stripTrailingZeros().toPlainString() + " km");
        } else if (dureeSec != null) {
            texte.append(dureeSec >= 60 ? dureeSec / 60 + " min" : dureeSec + " s");
        }
        if (allureSecKm != null && role != RoleBloc.RECUPERATION) {
            texte.append(" à ").append(allureSecKm / 60).append(':')
                    .append(String.format(Locale.ROOT, "%02d", allureSecKm % 60));
        }
        return texte.toString();
    }

    // --- Le repli -----------------------------------------------------------------------------

    /** Une seance qu'on n'a pas su lire reste dessinable : un bloc, sa distance, son allure. */
    private static List<BlocPrevu> blocUnique(PlannedSession seance, Map<String, Integer> legende) {
        BigDecimal distanceKm = seance.getDistanceCibleKm();
        Integer dureeSec = seance.getDureeCibleMin() == null ? null : seance.getDureeCibleMin() * 60;
        if (distanceKm == null && dureeSec == null) {
            return List.of();
        }
        Integer allureSecKm = legende.get("");
        RoleBloc role = seance.getType().estQualite() ? RoleBloc.EFFORT : RoleBloc.ENDURANCE;
        int duree = distanceKm != null
                ? distanceKm.multiply(BigDecimal.valueOf(allureSecKm == null ? ALLURE_PAR_DEFAUT : allureSecKm))
                        .intValue()
                : dureeSec;
        return List.of(new BlocPrevu(
                role, 1, distanceKm, dureeSec, allureSecKm, null, Math.max(30, duree),
                libelle(1, distanceKm, dureeSec, allureSecKm, role)));
    }

    /** Minuscules sans accents : « Échauffement » et « echauffement » doivent se lire pareil. */
    private static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
