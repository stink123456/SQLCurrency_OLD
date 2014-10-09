package com.sucy.sql;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.rit.sucy.config.Config;
import com.rit.sucy.player.PlayerUUIDs;
import com.rit.sucy.sql.ColumnType;
import com.rit.sucy.sql.direct.SQLEntry;
import com.rit.sucy.sql.direct.SQLTable;

/**
 * <p>A simple plugin that manages player currency balances over a server network
 * using a MySQL database.</p>
 */
public class SQLCurrency extends JavaPlugin implements Listener {

    private enum Action { ADD, SUBTRACT, SET };

    private final HashMap<UUID, Integer> funds = new HashMap<UUID, Integer>();

    private SQLTable table;
    private int defaultMoney;

    /**
     * <p>Enables the plugin, setting up the MySQL connections.</p>
     * <p>This should not ever be called by other plugins.</p>
     */
    @Override
    public void onEnable() {
        table = SQLManager.getTable(this, "players");
        table.createColumn("username", ColumnType.STRING_32);
        table.createColumn("balance", ColumnType.INT);
        saveDefaultConfig();
        Config.trim(getConfig());
        Config.setDefaults(getConfig());
        saveConfig();
        defaultMoney = getConfig().getInt("default-money");

        // Update the balance of online players
        for (Player player : getServer().getOnlinePlayers()) {
            new UpdateTask(player.getName(), player.getUniqueId()).runTaskAsynchronously(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * <p>Clears up event handlers before exiting.</p>
     */
    @Override
    public void onDisable() {
        HandlerList.unregisterAll((JavaPlugin)this);
    }

    /**
     * <p>Loads a player's balance when they join the server.</p>
     *
     * @param event event details
     */
    @EventHandler (priority = EventPriority.MONITOR)
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            new UpdateTask(event.getName(), event.getUniqueId()).run();
        }
    }

    /**
     * <p>Adds a specified amount to a player's balance.</p>
     *
     * @param player player to add to
     * @param amount amount to add to their balance
     */
    public void add(Player player, int amount) {
        if (-amount > get(player)) throw new IllegalArgumentException("The player does not have that much funds");
        funds.put(player.getUniqueId(), funds.get(player.getUniqueId()) + amount);
        new ModifyTask(player.getUniqueId().toString(), amount, Action.ADD).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, get(player)));
    }

    /**
     * <p>Subtracts a specified amount from a player's balance.</p>
     *
     * @param player player to subtract from
     * @param amount amount to subtract from their balance
     */
    public void subtract(Player player, int amount) {
        if (amount > get(player)) throw new IllegalArgumentException("The player does not have that much funds");
        funds.put(player.getUniqueId(), funds.get(player.getUniqueId()) - amount);
        new ModifyTask(player.getUniqueId().toString(), amount, Action.SUBTRACT).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, get(player)));
    }

    /**
     * <p>Sets the player's balance to a specified amount.</p>
     *
     * @param player player to set the balance for
     * @param amount amount to set the balance to
     */
    public void set(Player player, int amount) {
        if (amount < 0) throw new IllegalArgumentException("You cannot set a player's balance to below 0.");
        funds.put(player.getUniqueId(), amount);
        new ModifyTask(player.getUniqueId().toString(), amount, Action.SET).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, amount));
    }

    /**
     * <p>Retrieves the current balance of a player.</p>
     *
     * @param player player to retrieve the balance of
     * @return       the current balance of the player
     */
    public int get(Player player) {
        return funds.get(player.getUniqueId());
    }

    private class ModifyTask extends BukkitRunnable {
        private Action action;
        private String id;
        private int amount;
        public ModifyTask(String id, int amount, Action action) {
            this.id = id;
            this.amount = amount;
            this.action = action;
        }
        @Override
        public void run() {
            SQLEntry entry = table.createEntry(id);
            if (action == Action.SET) {
                entry.set("balance", amount);
            }
            else {
                int current = Math.max(entry.getInt("balance"), 0);
                if (action == Action.ADD) current += amount;
                else if (action == Action.SUBTRACT) current -= amount;
                entry.set("balance", current);
            }
        }
    }

    private class UpdateTask extends BukkitRunnable {
        private String name;
        private UUID id;
        public UpdateTask(String name, UUID id) {
            this.id = id;
            this.name = name;
        }
        public void run() {
            SQLEntry entry = table.createEntry(id.toString());
            entry.set("username", name);
            int balance = entry.getInt("balance");
            balance = balance == -1 ? defaultMoney : balance;
            funds.put(id, balance);
            entry.set("balance", balance);
        }
    }
}
