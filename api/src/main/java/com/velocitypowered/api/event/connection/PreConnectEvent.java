package com.velocitypowered.api.event.connection;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired before the player tries to connect to their first server.
 */
public final class PreConnectEvent {

    private final Player player;
    private RegisteredServer registeredServer;

    public PreConnectEvent(Player player, RegisteredServer registeredServer) {
        this.player = Preconditions.checkNotNull(player, "player");
        this.registeredServer = registeredServer;
    }

    public Player getPlayer() {
        return player;
    }

    public RegisteredServer getServer() {
        return registeredServer;
    }

    public void setProvider(@Nullable RegisteredServer registeredServer) {
        this.registeredServer = registeredServer;
    }

    @Override
    public String toString() {
        return "PreConnectEvent{" +
                "player=" + player +
                '}';
    }
}
