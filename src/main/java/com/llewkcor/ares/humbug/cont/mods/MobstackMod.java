package com.llewkcor.ares.humbug.cont.mods;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.llewkcor.ares.commons.util.bukkit.Scheduler;
import com.llewkcor.ares.commons.util.general.Configs;
import com.llewkcor.ares.commons.util.general.Time;
import com.llewkcor.ares.humbug.Humbug;
import com.llewkcor.ares.humbug.cont.HumbugMod;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MobstackMod implements HumbugMod, Listener {
    @Getter public final Humbug plugin;
    @Getter public final String name = "Mob Stacking";
    @Getter @Setter public boolean enabled;
    @Getter @Setter public String tagPrefix;
    @Getter @Setter public int stackInterval;
    @Getter @Setter public int maxStackSize;
    @Getter @Setter public int breedCooldown;
    @Getter public List<String> stackTypes;
    @Getter public final List<UUID> stackSkip;
    @Getter public final Map<UUID, Long> breedCooldowns;
    private BukkitTask stackTask;

    /**
     * All breed materials for the provided EntityType
     */
    private final ImmutableMap<EntityType, List<Material>> breedMaterials = ImmutableMap.<EntityType, List<Material>>builder()
            .put(EntityType.COW, ImmutableList.of(Material.WHEAT))
            .put(EntityType.SHEEP, ImmutableList.of(Material.WHEAT))
            .put(EntityType.MUSHROOM_COW, ImmutableList.of(Material.WHEAT))
            .put(EntityType.HORSE, ImmutableList.of(Material.GOLDEN_APPLE, Material.GOLDEN_CARROT))
            .put(EntityType.CHICKEN, ImmutableList.of(Material.MELON_SEEDS, Material.PUMPKIN_SEEDS, Material.SEEDS))
            .put(EntityType.PIG, ImmutableList.of(Material.CARROT, Material.CARROT_ITEM, Material.POTATO_ITEM, Material.POTATO))
            .put(EntityType.WOLF, ImmutableList.of(Material.PORK, Material.GRILLED_PORK, Material.RAW_BEEF, Material.COOKED_BEEF, Material.RAW_CHICKEN, Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON, Material.ROTTEN_FLESH))
            .put(EntityType.OCELOT, ImmutableList.of(Material.RAW_FISH, Material.COOKED_FISH))
            .put(EntityType.RABBIT, ImmutableList.of(Material.YELLOW_FLOWER, Material.CARROT_ITEM, Material.CARROT, Material.GOLDEN_CARROT))
            .build();

    public MobstackMod(Humbug plugin) {
        this.plugin = plugin;
        this.stackSkip = Collections.synchronizedList(Lists.newArrayList());
        this.breedCooldowns = Maps.newConcurrentMap();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void load() {
        if (!breedCooldowns.isEmpty()) {
            breedCooldowns.clear();
        }

        if (!stackSkip.isEmpty()) {
            stackSkip.clear();
        }

        if (stackTask != null) {
            stackTask.cancel();
            stackTask = null;
        }

        final YamlConfiguration config = Configs.getConfig(plugin, "config");

        this.enabled = config.getBoolean("mods.mob-stacking.enabled");
        this.tagPrefix = ChatColor.translateAlternateColorCodes('&', config.getString("mods.mob_stacking.tag_prefix"));
        this.stackInterval = config.getInt("mods.mob_stacking.stack_interval");
        this.maxStackSize = config.getInt("mods.mob_stacking.max_stack_size");
        this.breedCooldown = config.getInt("mods.mob_stacking.breed_cooldown");
        this.stackTypes = config.getStringList("mods.mob_stacking.stack_types");

        this.stackTask = new Scheduler(plugin).sync(() -> {
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (!stackTypes.contains(entity.getType().name())) {
                        continue;
                    }

                    if (stackSkip.contains(entity.getUniqueId())) {
                        continue;
                    }

                    if (entity.getHealth() <= 0.0 || entity.isDead()) {
                        continue;
                    }

                    if (entity.isLeashed()) {
                        continue;
                    }

                    if (entity.getCustomName() != null && !entity.getCustomName().startsWith(tagPrefix)) {
                        continue;
                    }

                    final List<LivingEntity> merge = Lists.newArrayList();
                    merge.add(entity);

                    for (Entity nearby : entity.getNearbyEntities(3, 3, 3)) {
                        if (!(nearby instanceof LivingEntity)) {
                            continue;
                        }

                        final LivingEntity nearbyLivingEntity = (LivingEntity)nearby;

                        if (!nearby.getType().equals(entity.getType())) {
                            continue;
                        }

                        if (stackSkip.contains(nearby.getUniqueId())) {
                            continue;
                        }

                        if (entity instanceof Ageable) {
                            final Ageable entityA = (Ageable)entity;
                            final Ageable entityB = (Ageable)nearbyLivingEntity;

                            if (entityA.isAdult() != entityB.isAdult()) {
                                continue;
                            }
                        }

                        if (entity instanceof Colorable) {
                            final Colorable entityA = (Colorable)entity;
                            final Colorable entityB = (Colorable)nearbyLivingEntity;

                            if (entityA.getColor() != entityB.getColor()) {
                                continue;
                            }
                        }

                        if (nearbyLivingEntity.isLeashed()) {
                            continue;
                        }

                        if (nearbyLivingEntity.getCustomName() != null && !nearbyLivingEntity.getCustomName().startsWith(tagPrefix))

                            if (merge.contains(nearbyLivingEntity)) {
                                continue;
                            }

                        merge.add(nearbyLivingEntity);
                    }

                    if (merge.size() > 1) {
                        stack(merge);
                    }
                }
            }

            stackSkip.clear();
        }).repeat(stackInterval * 20L, stackInterval * 20L).run();
    }

    @Override
    public void unload() {
        if (stackTask != null) {
            stackTask.cancel();
        }

        stackSkip.clear();
        breedCooldowns.clear();
    }

    /**
     * Performs a stack for all provided Living Entity
     * @param entities Living Entity
     */
    private void stack(List<LivingEntity> entities) {
        if (entities.size() <= 1) {
            return;
        }

        entities.sort((o1, o2) -> {
            final int stackA = getStackSize(o1);
            final int stackB = getStackSize(o2);
            return stackA - stackB;
        });

        Collections.reverse(entities);

        final LivingEntity host = entities.get(0);
        int size = getStackSize(host);

        for (LivingEntity merged : entities) {
            if (merged.getUniqueId().equals(host.getUniqueId())) {
                continue;
            }

            final int mergedSize = getStackSize(merged);

            if ((mergedSize + size) > maxStackSize) {
                continue;
            }

            size += getStackSize(merged);
            stackSkip.add(merged.getUniqueId());
            merged.remove();
        }

        host.setCustomName(tagPrefix + size);
        stackSkip.add(host.getUniqueId());
    }

    /**
     * Returns the size of the stack for the provided Living Entity
     *
     * Returns as 1 if entity is not a stack
     * @param entity Living Entity
     * @return Stack Size
     */
    private int getStackSize(LivingEntity entity) {
        if (entity.getCustomName() == null || !entity.getCustomName().startsWith(tagPrefix)) {
            return 1;
        }

        try {
            return Integer.parseInt(entity.getCustomName().replace(tagPrefix, ""));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /**
     * Returns true if the provided Living Entity is a stack
     * @param entity Living Entity
     * @return True if a stack
     */
    public boolean isStacked(LivingEntity entity) {
        return entity.getCustomName() != null && entity.getCustomName().startsWith(tagPrefix);
    }

    /**
     * Returns the cooldown for the provided player to breed stacked mobs
     * @param player Player
     * @return Time in millis before being able to breed mobs again
     */
    public long getBreedingCooldown(Player player) {
        return breedCooldowns.getOrDefault(player.getUniqueId(), 0L);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        final int stackSize = getStackSize(entity);

        if (!isStacked(entity)) {
            return;
        }

        final LivingEntity clone = (LivingEntity)entity.getWorld().spawnEntity(entity.getLocation(), entity.getType());

        if ((stackSize - 1) > 1) {
            clone.setCustomName(tagPrefix + (stackSize - 1));
        }

        clone.setNoDamageTicks(2);
        clone.setFireTicks(entity.getFireTicks());
        clone.setRemainingAir(entity.getRemainingAir());
        clone.setVelocity(entity.getVelocity());
        clone.setTicksLived(entity.getTicksLived());

        entity.getActivePotionEffects().forEach(clone::addPotionEffect);

        if (entity instanceof Colorable) {
            final Colorable entityA = (Colorable)entity;
            final Colorable entityB = (Colorable)clone;

            entityA.setColor(entityB.getColor());
        }

        if (entity instanceof Ageable) {
            final Ageable entityA = (Ageable)entity;
            final Ageable entityB = (Ageable)clone;

            entityA.setAge(entityB.getAge());
        }

        stackSkip.add(entity.getUniqueId());
    }

    @EventHandler
    public void onBreed(PlayerInteractEntityEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getRightClicked() instanceof LivingEntity)) {
            return;
        }

        final Player player = event.getPlayer();
        final UUID uniqueId = player.getUniqueId();
        final LivingEntity entity = (LivingEntity)event.getRightClicked();
        final ItemStack hand = player.getItemInHand();

        if (!breedMaterials.containsKey(entity.getType())) {
            return;
        }

        if (!breedMaterials.get(entity.getType()).contains(hand.getType())) {
            return;
        }

        if (!isStacked(entity)) {
            return;
        }

        event.setCancelled(true);

        final int stackSize = getStackSize(entity);

        if (stackSize <= 1) {
            player.sendMessage(ChatColor.RED + "Stack size must be at least 2 to begin breeding");
            return;
        }

        if (getBreedingCooldown(player) > Time.now()) {
            player.sendMessage(
                    ChatColor.RED + "You can not breed animals for another " +
                            ChatColor.RED + "" + ChatColor.BOLD + Time.convertToDecimal(getBreedingCooldown(player) - Time.now()) +
                            ChatColor.RED + "s");

            return;
        }

        final LivingEntity baby = (LivingEntity)entity.getWorld().spawnEntity(entity.getLocation(), entity.getType());

        if (baby instanceof Ageable) {
            ((Ageable)baby).setBaby();
        }

        if (baby instanceof Colorable) {
            final Colorable parent = (Colorable)entity;
            final Colorable colorable = (Colorable)baby;

            colorable.setColor(parent.getColor());
        }

        if (hand.getAmount() == 1) {
            player.getInventory().removeItem(hand);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }

        breedCooldowns.put(player.getUniqueId(), (Time.now() + (breedCooldown * 1000L)));
        new Scheduler(plugin).sync(() -> breedCooldowns.remove(uniqueId)).delay(breedCooldown * 20L).run();
    }
}
