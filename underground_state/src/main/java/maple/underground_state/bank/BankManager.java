package maple.underground_state.bank;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.logging.Logger;

public final class BankManager {

    private final BankDataDB db;
    private final CmiEconomyBridge economy;

    public BankManager(@NotNull BankDataDB db, @NotNull Logger logger) {
        this.db = db;
        this.economy = new CmiEconomyBridge(logger);
    }

    public long bankBalance(@NotNull UUID uuid) {
        return db.balance(uuid);
    }

    public long walletBalance(@NotNull Player player) {
        OptionalDouble balance = economy.balance(player);
        return balance.isPresent() ? Math.max(0L, (long) Math.floor(balance.getAsDouble())) : 0L;
    }

    public @NotNull BankOperationResult depositAll(@NotNull Player player) {
        return deposit(player, walletBalance(player));
    }

    public @NotNull BankOperationResult depositHalf(@NotNull Player player) {
        return deposit(player, walletBalance(player) / 2L);
    }

    public @NotNull BankOperationResult withdrawAll(@NotNull Player player) {
        return withdraw(player, bankBalance(player.getUniqueId()));
    }

    public @NotNull BankOperationResult withdrawHalf(@NotNull Player player) {
        return withdraw(player, bankBalance(player.getUniqueId()) / 2L);
    }

    public @NotNull BankOperationResult deposit(@NotNull Player player, long amount) {
        if (amount <= 0L) {
            return result(BankOperationResult.Status.INVALID_AMOUNT, 0L, player);
        }

        OptionalDouble wallet = economy.balance(player);
        if (wallet.isEmpty()) {
            return result(BankOperationResult.Status.ECONOMY_UNAVAILABLE, amount, player);
        }

        long walletBalance = Math.max(0L, (long) Math.floor(wallet.getAsDouble()));
        if (walletBalance < amount) {
            return new BankOperationResult(
                    BankOperationResult.Status.INSUFFICIENT_WALLET,
                    amount,
                    bankBalance(player.getUniqueId()),
                    walletBalance
            );
        }

        if (!economy.withdraw(player, amount)) {
            return result(BankOperationResult.Status.ECONOMY_ERROR, amount, player);
        }

        BankChangeResult change = db.changeBalance(player.getUniqueId(), amount, BankAction.DEPOSIT);
        if (!change.success()) {
            economy.deposit(player, amount);
            return result(BankOperationResult.Status.STORAGE_ERROR, amount, player);
        }

        return new BankOperationResult(
                BankOperationResult.Status.SUCCESS,
                amount,
                change.balanceAfter(),
                walletBalance(player)
        );
    }

    public @NotNull BankOperationResult withdraw(@NotNull Player player, long amount) {
        if (amount <= 0L) {
            return result(BankOperationResult.Status.INVALID_AMOUNT, 0L, player);
        }
        if (economy.balance(player).isEmpty()) {
            return result(BankOperationResult.Status.ECONOMY_UNAVAILABLE, amount, player);
        }

        BankChangeResult change = db.changeBalance(player.getUniqueId(), -amount, BankAction.WITHDRAW);
        if (!change.success()) {
            return new BankOperationResult(
                    change.insufficientFunds()
                            ? BankOperationResult.Status.INSUFFICIENT_BANK
                            : BankOperationResult.Status.STORAGE_ERROR,
                    amount,
                    change.balanceAfter(),
                    walletBalance(player)
            );
        }

        if (!economy.deposit(player, amount)) {
            db.changeBalance(player.getUniqueId(), amount, BankAction.DEPOSIT);
            return result(BankOperationResult.Status.ECONOMY_ERROR, amount, player);
        }

        return new BankOperationResult(
                BankOperationResult.Status.SUCCESS,
                amount,
                change.balanceAfter(),
                walletBalance(player)
        );
    }

    public int countLogs(@NotNull UUID uuid) {
        return db.countLogs(uuid);
    }

    public @NotNull List<BankLogEntry> loadLogs(@NotNull UUID uuid, int offset, int limit) {
        return db.loadLogs(uuid, offset, limit);
    }

    private @NotNull BankOperationResult result(@NotNull BankOperationResult.Status status,
                                                long amount,
                                                @NotNull Player player) {
        return new BankOperationResult(status, amount, bankBalance(player.getUniqueId()), walletBalance(player));
    }
}
