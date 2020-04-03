package com.llewkcor.ares.humbug.cont;

import com.google.common.collect.Sets;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.mods.*;
import lombok.Getter;

import java.util.Set;

public final class ModManager {
    @Getter public Humbug plugin;
    @Getter public Set<HumbugMod> mods;

    public ModManager(Humbug plugin) {
        this.plugin = plugin;
        this.mods = Sets.newConcurrentHashSet();

        mods.add(new WorldMod(plugin));
        mods.add(new ElevatorMod(plugin));
        mods.add(new MobstackMod(plugin));
        mods.add(new KitMod(plugin));
        mods.add(new KnockbackMod(plugin));
    }

    public void load() {
        mods.forEach(HumbugMod::load);
    }

    public void unload() {
        mods.forEach(HumbugMod::unload);
    }

    public void reload() {
        mods.forEach(HumbugMod::reload);
    }
}
