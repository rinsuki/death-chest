package com.github.devcyntrix.deathchest.listener;

import com.github.devcyntrix.deathchest.DeathChestPlugin;
import com.github.devcyntrix.deathchest.config.ChangeDeathMessageOptions;
import com.github.devcyntrix.deathchest.config.DeathChestConfig;
import com.github.devcyntrix.deathchest.config.GlobalNotificationOptions;
import com.github.devcyntrix.deathchest.config.PlayerNotificationOptions;
import me.clip.placeholderapi.PlaceholderAPI;
import org.apache.commons.text.StringSubstitutor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * This class is only for handling if a player dies. Here is the spawning of a death chest written and some checks if a
 * chest should spawn. Additionally, here is the death message, custom info and broadcast message implemented.
 */
public class SpawnChestListener implements Listener {

    private final DeathChestPlugin plugin;

    public SpawnChestListener(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates the death chest if the player dies.
     * It only spawns a chest if the player has the permission to build in the region.
     * It puts the drops into the chest and clears the drops.
     *
     * @param event the event from the bukkit api
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {

        ChangeDeathMessageOptions changeDeathMessageOptions = plugin.getDeathChestConfig().changeDeathMessageOptions();
        if (changeDeathMessageOptions.enabled()) {
            if (changeDeathMessageOptions.message() == null) {
                event.setDeathMessage(null);
                return;
            }
            Player player = event.getEntity();
            Location location = player.getLocation();
            if (location.getWorld() == null) {
                return; // Invalid location
            }

            StringSubstitutor substitutor = new StringSubstitutor(Map.of(
                    "x", location.getBlockX(),
                    "y", location.getBlockY(),
                    "z", location.getBlockZ(),
                    "world", location.getWorld().getName(),
                    "player_name", player.getName(),
                    "player_displayname", player.getDisplayName()));
            event.setDeathMessage(Arrays.stream(changeDeathMessageOptions.message()).map(substitutor::replace).map(s -> {
                if (DeathChestPlugin.isPlaceholderAPIEnabled()) {
                    return PlaceholderAPI.setPlaceholders(player, s);
                }
                return s;
            }).collect(Collectors.joining("\n")));
        }

        if (event.getKeepInventory())
            return;
        event.getDrops().removeIf(itemStack -> itemStack.getType() == Material.AIR); // Prevent spawning an empty chest
        if (event.getDrops().isEmpty())
            return;

        Player player = event.getEntity();
        Location deathLocation = new Location(
                player.getWorld(),
                player.getLocation().getX(),
                Math.round(player.getLocation().getY()),
                player.getLocation().getZ()
        );

        if (!plugin.getDeathChestConfig().worldFilterConfig().test(deathLocation.getWorld()))
            return;

        // Check Minecraft limitation of block positions
        if (deathLocation.getBlockY() <= player.getWorld().getMinHeight()) // Min build height
            return;
        if (deathLocation.getBlockY() >= player.getWorld().getMaxHeight()) // Max build height
            return;

        // Check protection
        boolean build = plugin.getProtectionService().canBuild(player, deathLocation, Material.CHEST);
        if (!build)
            return;

        DeathChestConfig deathChestConfig = plugin.getDeathChestConfig();
        Duration expiration = deathChestConfig.expiration();
        if (expiration == null)
            expiration = Duration.ofSeconds(-1);

        long createdAt = System.currentTimeMillis();
        long expireAt = !expiration.isNegative() && !expiration.isZero() ? createdAt + expiration.toMillis() : -1;

        Location loc = deathLocation.getBlock().getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        long start = System.currentTimeMillis();
        while (!plugin.canPlaceChestAt(loc)) {
            if (System.currentTimeMillis() - start > 1000) {
                loc.setY(loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()));
                break;
            }

            int x = random.nextInt(10) - 5;
            int z = random.nextInt(10) - 5;
            loc.add(x, 0, z);
        }

        try {
            boolean protectedChest = player.hasPermission(deathChestConfig.chestProtectionOptions().permission());
            plugin.createDeathChest(loc, createdAt, expireAt, player, protectedChest, event.getDrops().toArray(new ItemStack[0]));
            // Clears the drops
            event.getDrops().clear();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Player notification
        PlayerNotificationOptions playerNotificationOptions = deathChestConfig.playerNotificationOptions();
        if (playerNotificationOptions.enabled() && playerNotificationOptions.messages() != null) {
            if (deathLocation.getWorld() == null) {
                return; // Invalid location
            }
            StringSubstitutor substitutor = new StringSubstitutor(Map.of(
                    "x", deathLocation.getBlockX(),
                    "y", deathLocation.getBlockY(),
                    "z", deathLocation.getBlockZ(),
                    "world", deathLocation.getWorld().getName()));
            playerNotificationOptions.showNotification(player, substitutor);
        }

        // Global notification
        GlobalNotificationOptions globalNotificationOptions = deathChestConfig.globalNotificationOptions();
        if (globalNotificationOptions.enabled() && globalNotificationOptions.messages() != null) {
            if (deathLocation.getWorld() == null) {
                return; // Invalid location
            }
            StringSubstitutor substitutor = new StringSubstitutor(Map.of(
                    "x", deathLocation.getBlockX(),
                    "y", deathLocation.getBlockY(),
                    "z", deathLocation.getBlockZ(),
                    "world", deathLocation.getWorld().getName(),
                    "player_name", player.getName(),
                    "player_displayname", player.getDisplayName()));
            globalNotificationOptions.showNotification(player, substitutor);
        }


    }

}
