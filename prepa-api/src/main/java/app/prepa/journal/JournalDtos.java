package app.prepa.journal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs du journal. */
public final class JournalDtos {

    private JournalDtos() {}

    public record UpsertEntryRequest(
            @NotNull LocalDate date,
            @NotBlank String contenu,
            @Min(1) @Max(5) Short humeur,
            @Min(1) @Max(5) Short fatigue,
            BigDecimal sommeilH,
            Boolean douleur) {}

    public record EntryResponse(
            UUID id,
            LocalDate date,
            String contenu,
            Short humeur,
            Short fatigue,
            BigDecimal sommeilH,
            boolean douleur) {

        public static EntryResponse from(JournalEntry e) {
            return new EntryResponse(
                    e.getId(), e.getDate(), e.getContenu(), e.getHumeur(), e.getFatigue(), e.getSommeilH(),
                    e.isDouleur());
        }
    }
}
