package maple.underground_state.bank;

public record BankChangeResult(boolean success,
                               boolean insufficientFunds,
                               long balanceAfter) {

    public static BankChangeResult success(long balanceAfter) {
        return new BankChangeResult(true, false, balanceAfter);
    }

    public static BankChangeResult insufficient(long balanceAfter) {
        return new BankChangeResult(false, true, balanceAfter);
    }

    public static BankChangeResult failure() {
        return new BankChangeResult(false, false, 0L);
    }
}
