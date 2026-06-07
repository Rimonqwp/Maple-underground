package maple.underground_state.bank;

import org.jetbrains.annotations.NotNull;

public enum BankAction {
    DEPOSIT("存入"),
    WITHDRAW("取出");

    private final String displayName;

    BankAction(@NotNull String displayName) {
        this.displayName = displayName;
    }

    public @NotNull String displayName() {
        return displayName;
    }
}
