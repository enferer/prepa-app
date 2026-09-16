package app.prepa.athlete;

import app.prepa.domain.TypeSeance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs du profil sportif. */
public final class ProfileDtos {

    private ProfileDtos() {}

    public record ProfileResponse(
            Short fcMax,
            Short fcRepos,
            BigDecimal vmaKmh,
            BigDecimal volumeHabituelKm,
            List<String> joursDisponibles,
            boolean renfoActif,
            Short renfoFrequence,
            List<String> renfoMateriel,
            String renfoFocus,
            String notes,
            int seuilFcEnduranceFondamentale) {

        public static ProfileResponse from(AthleteProfile p) {
            return new ProfileResponse(
                    p.getFcMax(), p.getFcRepos(), p.getVmaKmh(), p.getVolumeHabituelKm(),
                    liste(p.getJoursDisponibles()), p.isRenfoActif(), p.getRenfoFrequence(),
                    liste(p.getRenfoMateriel()), p.getRenfoFocus(), p.getNotes(),
                    p.seuilFcEnduranceFondamentale());
        }

        private static List<String> liste(String[] valeurs) {
            return valeurs == null ? List.of() : List.of(valeurs);
        }
    }

    public record UpdateProfileRequest(
            Short fcMax,
            Short fcRepos,
            BigDecimal vmaKmh,
            BigDecimal volumeHabituelKm,
            List<String> joursDisponibles,
            Boolean renfoActif,
            Short renfoFrequence,
            List<String> renfoMateriel,
            String renfoFocus,
            String notes) {}

    public record RecordRequest(
            @NotNull @Positive Integer distanceM,
            @NotNull @Positive Integer tempsSec,
            @NotNull LocalDate date,
            String contexte) {}

    public record RecordResponse(
            UUID id, int distanceM, int tempsSec, LocalDate date, String contexte, String source, int allureSecKm) {

        public static RecordResponse from(PersonalRecord r) {
            return new RecordResponse(
                    r.getId(), r.getDistanceM(), r.getTempsSec(), r.getDate(), r.getContexte(), r.getSource(),
                    r.allureSecKm());
        }
    }

    public record InjuryRequest(
            @NotBlank String zone, String statut, Short palier, String consignes,
            @NotNull LocalDate debut, LocalDate fin) {}

    public record InjuryResponse(
            UUID id, String zone, String statut, Short palier, String consignes, LocalDate debut, LocalDate fin) {

        public static InjuryResponse from(Injury i) {
            return new InjuryResponse(
                    i.getId(), i.getZone(), i.getStatut(), i.getPalier(), i.getConsignes(), i.getDebut(), i.getFin());
        }
    }

    public record ConstraintRequest(@NotBlank String type, LocalDate debut, LocalDate fin, @NotBlank String detail) {}

    public record ConstraintResponse(UUID id, String type, LocalDate debut, LocalDate fin, String detail) {

        public static ConstraintResponse from(AthleteConstraint c) {
            return new ConstraintResponse(c.getId(), c.getType(), c.getDebut(), c.getFin(), c.getDetail());
        }
    }

    public record SignatureSessionRequest(
            @NotBlank String nom,
            @NotNull TypeSeance typeSeance,
            String description,
            BigDecimal distanceKm,
            String frequenceSouhaitee,
            String contexte,
            Boolean actif) {}

    public record SignatureSessionResponse(
            UUID id, String nom, TypeSeance typeSeance, String description, BigDecimal distanceKm,
            String frequenceSouhaitee, String contexte, boolean actif) {

        public static SignatureSessionResponse from(SignatureSession s) {
            return new SignatureSessionResponse(
                    s.getId(), s.getNom(), s.getTypeSeance(), s.getDescription(), s.getDistanceKm(),
                    s.getFrequenceSouhaitee(), s.getContexte(), s.isActif());
        }
    }
}
