package maple.underground_state.bank;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.OptionalDouble;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CmiEconomyBridge {

    private static final String CMI_PLUGIN_NAME = "CMI";
    private static final String CMI_USER_CLASS_NAME = "com.Zrips.CMI.Containers.CMIUser";

    private final Logger logger;
    private boolean attemptedHook;
    private boolean available;
    private Method getUserMethod;
    private Method getBalanceMethod;
    private Method depositMethod;
    private Method withdrawMethod;

    public CmiEconomyBridge(@NotNull Logger logger) {
        this.logger = logger;
    }

    public OptionalDouble balance(@NotNull Player player) {
        Object user = user(player);
        if (user == null) {
            return OptionalDouble.empty();
        }
        try {
            Object result = getBalanceMethod.invoke(user);
            if (result instanceof Number number) {
                return OptionalDouble.of(number.doubleValue());
            }
        } catch (ReflectiveOperationException ex) {
            logger.log(Level.WARNING, "Failed to read CMI balance for " + player.getName(), ex);
            available = false;
        }
        return OptionalDouble.empty();
    }

    public boolean deposit(@NotNull Player player, long amount) {
        return change(player, amount, depositMethod);
    }

    public boolean withdraw(@NotNull Player player, long amount) {
        return change(player, amount, withdrawMethod);
    }

    private boolean change(@NotNull Player player, long amount, @Nullable Method method) {
        if (amount <= 0L || method == null) {
            return false;
        }
        Object user = user(player);
        if (user == null) {
            return false;
        }
        try {
            method.invoke(user, Double.valueOf(amount));
            return true;
        } catch (ReflectiveOperationException ex) {
            logger.log(Level.WARNING, "Failed to change CMI balance for " + player.getName(), ex);
            available = false;
            return false;
        }
    }

    private @Nullable Object user(@NotNull Player player) {
        if (!hook()) {
            return null;
        }
        try {
            return getUserMethod.invoke(null, player);
        } catch (ReflectiveOperationException ex) {
            logger.log(Level.WARNING, "Failed to resolve CMI user for " + player.getName(), ex);
            available = false;
            return null;
        }
    }

    private boolean hook() {
        if (attemptedHook) {
            return available;
        }
        attemptedHook = true;

        Plugin cmiPlugin = Bukkit.getPluginManager().getPlugin(CMI_PLUGIN_NAME);
        if (cmiPlugin == null || !cmiPlugin.isEnabled()) {
            return false;
        }

        try {
            Class<?> userClass = Class.forName(CMI_USER_CLASS_NAME);
            getUserMethod = userClass.getMethod("getUser", Player.class);
            getBalanceMethod = userClass.getMethod("getBalance");
            depositMethod = userClass.getMethod("deposit", Double.class);
            withdrawMethod = userClass.getMethod("withdraw", Double.class);
            available = true;
            return true;
        } catch (ReflectiveOperationException ex) {
            logger.log(Level.WARNING, "Failed to hook CMI economy API", ex);
            available = false;
            return false;
        }
    }
}
