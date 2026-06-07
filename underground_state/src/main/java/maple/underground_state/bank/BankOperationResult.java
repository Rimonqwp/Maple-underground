package maple.underground_state.bank;

import org.jetbrains.annotations.NotNull;

public record BankOperationResult(@NotNull Status status,
                                  long amount,
                                  long bankBalance,
                                  long walletBalance) {

    public enum Status {
        SUCCESS,
        ECONOMY_UNAVAILABLE,
        INVALID_AMOUNT,
        INSUFFICIENT_WALLET,
        INSUFFICIENT_BANK,
        STORAGE_ERROR,
        ECONOMY_ERROR
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }
}
