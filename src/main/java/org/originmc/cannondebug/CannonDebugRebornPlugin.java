/*
 * This file is part of CannonProfiler, licensed under the MIT License (MIT).
 *
 * Copyright (c) Origin <http://www.originmc.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.originmc.cannondebug;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.originmc.cannondebug.cmd.CommandType;
import org.originmc.cannondebug.listener.PlayerListener;
import org.originmc.cannondebug.listener.WorldListener;
import org.originmc.cannondebug.utils.Configuration;
import org.originmc.cannondebug.utils.EnumUtils;
import org.originmc.cannondebug.utils.MaterialUtils;
import org.originmc.cannondebug.utils.NumberUtils;
import xyz.fragmentmc.uiwrapper.listener.EntityMergeListener;
import xyz.fragmentmc.uiwrapper.listener.MultiDispenserListener;

import java.util.*;

import static org.bukkit.ChatColor.*;

public final class CannonDebugRebornPlugin extends JavaPlugin implements Runnable {
    public static CannonDebugRebornPlugin INSTANCE;

    @Getter
    private final Map<UUID, User> users = new HashMap<>();

    @Getter
    private final Map<Location, List<BlockSelection>> selections = new HashMap<>();

    @Getter
    private final List<EntityTracker> activeTrackers = new ArrayList<>();

    @Getter
    private final Set<Integer> trackedEntities = new HashSet<>();

    @Getter
    @Setter
    private long currentTick = 0;

    @Getter
    private WorldListener worldListener;

    @Getter
    private Configuration configuration;

    public User getUser(UUID playerId) {
        // Return null if the player id has no user profile attached.
        if (!users.containsKey(playerId)) return null;

        // Return the user.
        return users.get(playerId);
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        configuration = new Configuration(this);
        configuration.loadConfiguration();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(worldListener = new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityMergeListener(), this);
        getServer().getPluginManager().registerEvents(new MultiDispenserListener(), this);
        getServer().getScheduler().runTaskTimer(this, this, 1, 1);

        // Load user profiles.
        for (Player player : getServer().getOnlinePlayers()) {
            users.put(player.getUniqueId(), new User(player));
        }
    }

    @Override
    public void run() {
        worldListener.tick();

        // Loop through every active tracker.
        Iterator<EntityTracker> iterator = activeTrackers.iterator();
        while (iterator.hasNext()) {
            // Add new location and velocity to the tracker histories.
            EntityTracker tracker = iterator.next();
            tracker.getLocationHistory().add(tracker.getEntity().getLocation());
            tracker.getVelocityHistory().add(tracker.getEntity().getVelocity());

            // Remove dead entities from tracker.
            if (tracker.getEntity().isDead()) {
                tracker.setDeathTick(currentTick);
                trackedEntities.remove(tracker.getEntity().getEntityId());
                tracker.setEntity(null);
                iterator.remove();
            }
        }

        // Reset order
        for (User user : users.values()) {
            user.setOrder(-1);
        }

        // Increment the tick counter.
        currentTick++;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        return CommandType.fromCommand(this, sender, args).execute();
    }

    /**
     * Attempts to either add or remove a selection depending on whether or not
     * the user already had this position set.
     *
     * @param user  the user that is adding to their selection.
     * @param block the block to select.
     */
    public void handleSelection(User user, Block block) {
        // Do nothing if not a selectable block.
        if (!MaterialUtils.isSelectable(block.getType(), configuration.alternativeTracking)) return;

        // Attempt to deselect block if it is already selected.
        BlockSelection selection = user.getSelection(block.getLocation());
        Player player = user.getBase();
        if (selection != null) {
            // Inform the player.
            player.sendMessage(String.format(RED + "" + BOLD + "REM " + WHITE + "%s @ %s %s %s " + GRAY + "ID: %s",
                    EnumUtils.getFriendlyName(block.getType()), block.getX(), block.getY(), block.getZ(), selection.getId()));

            // Remove the clicked location.
            user.getSelections().remove(selection);

            List<BlockSelection> list = selections.get(selection.getLocation());

            if (list != null) {
                if (list.size() == 1) {
                    selections.remove(selection.getLocation());
                } else {
                    list.remove(selection);
                }
            }

            // Update users preview.
            if (user.isPreviewing()) {
                getServer().getScheduler().runTask(this, () ->
                        player.sendBlockChange(block.getLocation(), block.getBlockData()));
            }
            return;
        }

        // Do nothing if the user has too many selections.
        int max = NumberUtils.getNumericalPerm(player, "cannondebug.maxselections.");
        if (user.getSelections().size() >= max) {
            player.sendMessage(String.format(RED + "You have too many selections! " + GRAY + "(Max = %s)", max));
            return;
        }

        // Update users preview.
        if (user.isPreviewing()) {
            getServer().getScheduler().runTask(this, () ->
                    player.sendBlockChange(block.getLocation(), Material.EMERALD_BLOCK.createBlockData()));
        }

        // Add the selected location.
        selection = user.addSelection(block.getLocation());

        Location spawnLocation = selection.getLocation();
        List<BlockSelection> list = selections.computeIfAbsent(spawnLocation, k -> new ArrayList<>());
        list.add(selection);

        // Inform the player.
        player.sendMessage(String.format(GREEN + "" + BOLD + "ADD " + WHITE + "%s @ %s %s %s " + GRAY + "ID: %s",
                EnumUtils.getFriendlyName(block.getType()), block.getX(), block.getY(), block.getZ(), selection.getId()));
    }

}
