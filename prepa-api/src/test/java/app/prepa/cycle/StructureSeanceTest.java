package app.prepa.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import app.prepa.cycle.StructureSeance.BlocPrevu;
import app.prepa.cycle.StructureSeance.RoleBloc;
import app.prepa.domain.TypeSeance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le parser de deroule, eprouve sur les seances telles que le coach les ecrit reellement.
 *
 * <p>Les libelles de ces tests sont copies de la base : c'est la seule maniere honnete de
 * verifier une lecture de langage naturel. Une formulation nouvelle qui ne se lirait pas doit
 * retomber sur le bloc unique, jamais produire un dessin faux.
 */
class StructureSeanceTest {

    private static PlannedSession seance(
            TypeSeance type, String titre, String description, String allures, String distanceKm) {
        PlannedSession s = new PlannedSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), type, titre);
        s.setDescription(description);
        s.setAlluresTexte(allures);
        if (distanceKm != null) {
            s.setDistanceCibleKm(new BigDecimal(distanceKm));
        }
        return s;
    }

    @Test
    @DisplayName("Une séance à intervalles se lit échauffement, blocs, retour au calme")
    void intervalles() {
        List<BlocPrevu> blocs = StructureSeance.deduire(seance(
                TypeSeance.AM,
                "Dernier rappel — 3×1 km AM",
                "2 km éch. + 3×1 km à AM (5:41) récup 2 min + 2 km RAC.",
                "AM 5:41 · éch/RAC 6:35",
                "7.00"));

        assertThat(blocs).hasSize(3);
        assertThat(blocs.get(0).role()).isEqualTo(RoleBloc.ECHAUFFEMENT);
        assertThat(blocs.get(0).distanceKm()).isEqualByComparingTo("2");
        assertThat(blocs.get(0).allureSecKm()).isEqualTo(395);

        BlocPrevu effort = blocs.get(1);
        assertThat(effort.role()).isEqualTo(RoleBloc.EFFORT);
        assertThat(effort.repetitions()).isEqualTo(3);
        assertThat(effort.distanceKm()).isEqualByComparingTo("1");
        assertThat(effort.allureSecKm()).isEqualTo(341);
        assertThat(effort.recupSec()).isEqualTo(120);
        // Trois kilometres a 5:41, plus les deux recuperations qui les separent.
        assertThat(effort.dureeEstimeeSec()).isEqualTo(3 * 341 + 2 * 120);
        assertThat(effort.libelle()).isEqualTo("3×1 km à 5:41");

        assertThat(blocs.get(2).role()).isEqualTo(RoleBloc.RETOUR_AU_CALME);
    }

    @Test
    @DisplayName("Un bloc d'allure dans une sortie longue est un effort, même sans répétition")
    void blocDansUneSortieLongue() {
        List<BlocPrevu> blocs = StructureSeance.deduire(seance(
                TypeSeance.SL,
                "Dernière sortie longue",
                "12 km : 8 km EF + 4 km à AM (5:41).",
                "EF 6:15 · bloc AM 5:41",
                "12.00"));

        assertThat(blocs).hasSize(2);
        assertThat(blocs.get(0).role()).isEqualTo(RoleBloc.ENDURANCE);
        assertThat(blocs.get(0).distanceKm()).isEqualByComparingTo("8");
        assertThat(blocs.get(1).role()).isEqualTo(RoleBloc.EFFORT);
        assertThat(blocs.get(1).allureSecKm()).isEqualTo(341);
    }

    @Test
    @DisplayName("Les lignes droites ne sont pas des blocs d'effort")
    void lignesDroites() {
        List<BlocPrevu> blocs = StructureSeance.deduire(seance(
                TypeSeance.EF,
                "Footing très facile",
                "6 km EF très facile + 4×20 s accélérations légères.",
                "EF 6:35 (facile)",
                "6.00"));

        assertThat(blocs).hasSize(2);
        assertThat(blocs.get(0).role()).isEqualTo(RoleBloc.ENDURANCE);
        assertThat(blocs.get(1).role()).isEqualTo(RoleBloc.LIGNES);
        assertThat(blocs.get(1).repetitions()).isEqualTo(4);
        assertThat(blocs.get(1).dureeSec()).isEqualTo(20);
    }

    @Test
    @DisplayName("Des accélérations comptées en toutes lettres se lisent aussi")
    void repetitionsEnToutesLettres() {
        List<BlocPrevu> blocs = StructureSeance.deduire(seance(
                TypeSeance.EF,
                "Footing + lignes",
                "5 km EF + 4 accélérations de 100 m à l'allure marathon.",
                "EF 6:40 · 4 lignes AM 5:41",
                "5.00"));

        assertThat(blocs).hasSize(2);
        assertThat(blocs.get(1).role()).isEqualTo(RoleBloc.LIGNES);
        assertThat(blocs.get(1).repetitions()).isEqualTo(4);
        assertThat(blocs.get(1).distanceKm()).isEqualByComparingTo("0.100");
        assertThat(blocs.get(1).allureSecKm()).isEqualTo(341);
    }

    @Test
    @DisplayName("Une description qui ne se laisse pas lire retombe sur un bloc unique")
    void repliSurLaDistanceCible() {
        List<BlocPrevu> blocs = StructureSeance.deduire(seance(
                TypeSeance.COURSE,
                "MARATHON",
                "Le grand jour ! Pars à 5:41/km sans t'emballer sur les premiers km.",
                "AM 5:41 (objectif 4h00)",
                "42.20"));

        assertThat(blocs).hasSize(1);
        assertThat(blocs.getFirst().distanceKm()).isEqualByComparingTo("42.20");
        assertThat(blocs.getFirst().allureSecKm()).isEqualTo(341);
    }

    @Test
    @DisplayName("Un footing simple donne un bloc, pas un dessin trompeur")
    void footingSimple() {
        List<BlocPrevu> blocs = StructureSeance.deduire(
                seance(TypeSeance.EF, "Footing déblocage", "6 km EF très tranquille.", "6:40", "6.00"));

        assertThat(blocs).hasSize(1);
        assertThat(blocs.getFirst().role()).isEqualTo(RoleBloc.ENDURANCE);
        assertThat(blocs.getFirst().allureSecKm()).isEqualTo(400);
        assertThat(blocs.getFirst().dureeEstimeeSec()).isEqualTo(2400);
    }

    @Test
    @DisplayName("L'annonce du total qui ouvre une sortie longue n'est pas un bloc")
    void enteteDeSortieLongue() {
        List<BlocPrevu> blocs = StructureSeance.deduire(seance(
                TypeSeance.SL,
                "SL 24 km",
                "SL 24 km : 9 km EF (6:15) + 2×4 km à AM (5:41) récup 1 km EF + 6 km EF.",
                "EF 6:15 · AM 5:41",
                "24.00"));

        assertThat(blocs).hasSize(3);
        // Sans cette lecture, le premier bloc annoncerait les vingt-quatre kilometres entiers,
        // suivis des morceaux qui les composent.
        assertThat(blocs.getFirst().distanceKm()).isEqualByComparingTo("9");
        assertThat(blocs.get(1).role()).isEqualTo(RoleBloc.EFFORT);
        // L'allure du bloc est celle qu'il annonce, pas celle de la recuperation qui le suit.
        assertThat(blocs.get(1).allureSecKm()).isEqualTo(341);
        // Une recuperation comptee en kilometres se trotte : elle occupe du temps sur le dessin.
        assertThat(blocs.get(1).recupSec()).isEqualTo(360);
    }

    @Test
    @DisplayName("Une récupération notée en minutes-secondes n'est pas une allure")
    void recuperationEnMinutesSecondes() {
        List<BlocPrevu> blocs = StructureSeance.deduire(seance(
                TypeSeance.SEUIL,
                "Seuil 3×2 km",
                "2 km éch. + 3×2 km au seuil (5:15) récup 2:30 + 1 km RAC.",
                "Seuil 5:15 · éch/RAC 6:35",
                "9.00"));

        assertThat(blocs.get(1).allureSecKm()).isEqualTo(315);
        assertThat(blocs.get(1).recupSec()).isEqualTo(150);
    }

    @Test
    @DisplayName("Des répétitions courtes sans allure annoncée sont des lignes, pas de la qualité")
    void repetitionsCourtesSansAllure() {
        List<BlocPrevu> blocs = StructureSeance.deduire(
                seance(TypeSeance.EF, "Footing", "6 km EF + 5×20 s.", "EF 6:35 (facile)", "6.00"));

        assertThat(blocs.get(1).role()).isEqualTo(RoleBloc.LIGNES);
    }

    @Test
    @DisplayName("Le déroulé posé par le coach l'emporte sur la relecture de la phrase")
    void laSaisieLEmporte() {
        PlannedSession s = seance(
                TypeSeance.SL,
                "Sortie longue 26 km",
                // La phrase dit une chose, le coach en a posé une autre : c'est la sienne qui vaut.
                "SL 26 km : 10 km EF + 2×3 km à AM récup 2 km EF + 8 km EF.",
                "EF 6:15 · blocs AM 5:41",
                "26.00");
        s.setStructure(List.of(
                new BlocSeance(RoleBloc.ENDURANCE, 1, new BigDecimal("12"), null, 375, null),
                new BlocSeance(RoleBloc.EFFORT, 2, new BigDecimal("3"), null, 341, 720),
                new BlocSeance(RoleBloc.ENDURANCE, 1, new BigDecimal("6"), null, 375, null)));

        List<BlocPrevu> blocs = StructureSeance.deduire(s);

        assertThat(blocs).hasSize(3);
        assertThat(blocs.getFirst().distanceKm()).isEqualByComparingTo("12");
        assertThat(blocs.get(1).repetitions()).isEqualTo(2);
        assertThat(blocs.get(1).recupSec()).isEqualTo(720);
        assertThat(blocs.get(1).dureeEstimeeSec()).isEqualTo(2 * 3 * 341 + 720);
        assertThat(blocs.get(1).libelle()).isEqualTo("2×3 km à 5:41");
    }

    @Test
    @DisplayName("Un déroulé vidé fait repasser la séance par sa description")
    void unDerouleVideRetombeSurLaPhrase() {
        PlannedSession s = seance(
                TypeSeance.AM, "Rappel AM", "2 km éch. + 3×1 km à AM (5:41) récup 2 min + 2 km RAC.",
                "AM 5:41 · éch/RAC 6:35", "7.00");
        s.setStructure(List.of());

        assertThat(StructureSeance.deduire(s)).hasSize(3);
    }

    @Test
    @DisplayName("Un bloc qui ne dit ni longueur ni durée ne se dessine pas")
    void blocSansGrandeur() {
        PlannedSession s = seance(TypeSeance.EF, "Footing", "6 km EF.", "EF 6:35", "6.00");
        s.setStructure(List.of(
                new BlocSeance(RoleBloc.ENDURANCE, 1, new BigDecimal("6"), null, 395, null),
                new BlocSeance(RoleBloc.LIGNES, 4, null, null, null, null)));

        assertThat(StructureSeance.deduire(s)).hasSize(1);
    }

    @Test
    @DisplayName("Ce qui ne se court pas n'a pas de déroulé")
    void renfoEtRepos() {
        assertThat(StructureSeance.deduire(seance(TypeSeance.REPOS, "Repos", null, null, null))).isEmpty();
        assertThat(StructureSeance.deduire(
                        seance(TypeSeance.RENFO, "Gainage", "3 séries de 5 exercices.", null, null)))
                .isEmpty();
    }
}
