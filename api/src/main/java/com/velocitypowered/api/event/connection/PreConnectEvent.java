package com.velocitypowered.api.event.connection;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This event is fired before the player tries to connect to their first server.
 */
public final class PreConnectEvent implements ResultedEvent<ResultedEvent.ComponentResult> {

    private final Player player;
    private RegisteredServer registeredServer;
    private ComponentResult result;

    public PreConnectEvent(Player player, RegisteredServer registeredServer) {
        this.player = Preconditions.checkNotNull(player, "player");
        this.registeredServer = registeredServer;
        this.result = ComponentResult.allowed();
    }

    public Player getPlayer() {
        return player;
    }

    public RegisteredServer getServer() {
        return registeredServer;
    }

    public void setServer(@Nullable RegisteredServer registeredServer) {
        this.registeredServer = registeredServer;
    }

    @Override
    public String toString() {
        return "PreConnectEvent{" +
                "player=" + player +
                ", registeredServer=" + registeredServer +
                ", result=" + result +
                '}';
    }

    @Override
    public ComponentResult getResult() {
        return result;
    }

    @Override
    public void setResult(ComponentResult result) {
        this.result = Preconditions.checkNotNull(result, "result");
    }
}
