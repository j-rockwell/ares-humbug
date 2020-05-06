package com.playares.humbug.event;

import com.playares.humbug.cont.mods.MiningMod;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

public final class FoundOreEvent extends Event implements Cancellable {
    @Getter public static final HandlerList handlerList = new HandlerList();
    @Getter @Setter public boolean cancelled;
    @Getter public final MiningMod.Findable findable;
    @Getter public final List<Block> blocks;

    public FoundOreEvent(MiningMod.Findable findable, List<Block> blocks) {
        this.findable = findable;
        this.blocks = blocks;
        this.cancelled = false;
    }

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }
}