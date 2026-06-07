package maple.underground_state.bank;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public record BankLogEntry(@NotNull BankAction action,
                           long amount,
                           long balanceAfter,
                           @NotNull Instant createdAt) {
}
