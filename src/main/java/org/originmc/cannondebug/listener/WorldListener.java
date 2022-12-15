package org.originmc.cannondebug.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.material.Dispenser;
import org.originmc.cannondebug.BlockSelection;
import org.originmc.cannondebug.CannonDebugRebornPlugin;
import org.originmc.cannondebug.EntityTracker;
import org.originmc.cannondebug.User;

import java.util.List;

import static org.originmc.cannondebug.utils.MaterialUtils.*;

public class WorldListener implements Listener {

    private final CannonDebugRebornPlugin plugin;

    public WorldListener(CannonDebugRebornPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        if (!plugin.getConfiguration().alternativeTracking) {
            return;
        }

        // Track recently spawned
        for (World world : Bukkit.getWorlds()) {
            List<Entity> entities = world.getEntities();

            for (Entity entity : entities) {
                if (entity.getTicksLived() != 1) {
                    continue;
                }

                track(entity);
            }
        }
    }

    private void track(Entity entity) {
        Location sourceLocation;
        if (entity instanceof TNTPrimed || entity instanceof FallingBlock) {
            sourceLocation = entity.getOrigin();
        } else {
            return;
        }

        if (sourceLocation.getX() % 1 != 0 || sourceLocation.getZ() % 1 != 0) {
            sourceLocation = sourceLocation.clone();
            sourceLocation.subtract(0.5, 0.0, 0.5);
        }

        if (plugin.getSelections().containsKey(sourceLocation)) {
            List<BlockSelection> blockSelections = plugin.getSelections().get(sourceLocation);

            EntityTracker tracker = null;
            for (BlockSelection selection : blockSelections) {
                // Build a new tracker due to it being used.
                if (tracker == null) {
                    tracker = new EntityTracker(entity.getType(), plugin.getCurrentTick());
                    tracker.setEntity(entity);
                    plugin.getActiveTrackers().add(tracker);
                    plugin.getTrackedEntities().add(entity.getEntityId());
                }

                // Update order
                selection.setOrder(selection.getUser().getAndIncOrder());

                // Add entity tracker to user selection
                selection.setTracker(tracker);
            }
        }
    }

    public boolean doDispenserTracking(Block block, boolean newEntity, Entity entity) {
        // Loop through each user profile.
        boolean returns = false;
        BlockSelection selection;
        EntityTracker tracker = null;
        for (User user : plugin.getUsers().values()) {
            // Do nothing if user is not attempting to profile current block.
            selection = user.getSelection(block.getLocation());
            if (selection == null) {
                continue;
            }
            // Build a new tracker due to it being used.
            if (tracker == null) {
                if(newEntity) {
                    // Cancel the event.
                    returns = true;
                    // Shoot a new falling block with the exact same properties as current.
                    BlockFace face = ((Dispenser) block.getState().getData()).getFacing();
                    Location location = block.getLocation().clone();
                    location.add(face.getModX() + 0.5, face.getModY(), face.getModZ() + 0.5);
                    entity = block.getWorld().spawn(location, TNTPrimed.class);
                }
                tracker = new EntityTracker(entity.getType(), plugin.getCurrentTick());
                tracker.setEntity(entity);
                plugin.getActiveTrackers().add(tracker);
                plugin.getTrackedEntities().add(entity.getEntityId());
            }

            // Update order
            selection.setOrder(user.getAndIncOrder());

            // Add block tracker to user.
            selection.setTracker(tracker);
        }
        return returns;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void startProfiling(BlockDispenseEvent event) {
        if (plugin.getConfiguration().alternativeTracking) {
            return;
        }
        // Do nothing if block is not a dispenser.
        Block block = event.getBlock();
        if (!isDispenser(block.getType())) return;

        // Do nothing if not shot TNT.
        if (!isExplosives(event.getItem().getType())) return;
        if (doDispenserTracking(block, true, null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void startProfiling(EntityChangeBlockEvent event) {
        if (plugin.getConfiguration().alternativeTracking) {
            return;
        }
        // Do nothing if the material is not used for stacking in cannons.
        Block block = event.getBlock();
        if (!isStacker(block.getType())) return;

        // Do nothing if block is not turning into a falling block.
        if (!(event.getEntity() instanceof FallingBlock)) return;

        // Loop through each user profile.
        BlockSelection selection;
        EntityTracker tracker = null;
        for (User user : plugin.getUsers().values()) {
            // Do nothing if user is not attempting to profile current block.
            selection = user.getSelection(block.getLocation());
            if (selection == null) {
                continue;
            }

            // Build a new tracker due to it being used.
            if (tracker == null) {
                tracker = new EntityTracker(event.getEntityType(), plugin.getCurrentTick());
                tracker.setEntity(event.getEntity());
                plugin.getActiveTrackers().add(tracker);
                plugin.getTrackedEntities().add(event.getEntity().getEntityId());
            }

            // Update order
            selection.setOrder(user.getAndIncOrder());

            // Add block tracker to user.
            selection.setTracker(tracker);
        }
    }

}
