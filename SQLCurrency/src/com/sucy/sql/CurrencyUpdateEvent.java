package com.sucy.sql;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * <p>An event for when a player's balance is modified by another plugin.</p>
 */
public class CurrencyUpdateEvent extends Event {

    private static HandlerList handlerList = new HandlerList();

    private Player player;
    private int balance;

    /**
     * <p>Initializes a new event.</p>
     *
     * @param player  player that had their balance modified
     * @param balance the new balance of the player
     */
    public CurrencyUpdateEvent(Player player, int balance) {
        this.player = player;
        this.balance = balance;
    }

    /**
     * <p>Retrieves the player that had their balance modified.</p>
     *
     * @return the player that had their balance modified
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * <p>Retrieves the current balance of the player.</p>
     *
     * @return the current balance of the player
     */
    public int getBalance() {
        return balance;
    }

    /**
     * <p>Retrieves the handler list for the event.</p>
     *
     * @return the handler list for the event
     */
    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }

    /**
     * <p>Retrieves the handler list for the event.</p>
     *
     * @return the handler list for the event
     */
    public static HandlerList getHandlerList() {
        return handlerList;
    }
}
