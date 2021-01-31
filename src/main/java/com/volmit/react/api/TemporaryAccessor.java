package com.volmit.react.api;

import org.bukkit.entity.Player;
import primal.lang.collection.GSet;

import java.util.Arrays;

public class TemporaryAccessor {
    private final Player player;
    private final GSet<Permissable> permissions;

    public TemporaryAccessor(Player player) {
        this.player = player;
        permissions = new GSet<Permissable>();
    }

    public Player getPlayer() {
        return player;
    }

    public GSet<Permissable> getPermissions() {
        return permissions;
    }

    public void addPermission(Permissable perm) {
        permissions.add(perm);
    }

    public void addAll() {
        permissions.addAll(Arrays.asList(Permissable.values()));
    }
}
