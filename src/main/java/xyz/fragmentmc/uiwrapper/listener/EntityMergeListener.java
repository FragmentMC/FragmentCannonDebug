package xyz.fragmentmc.uiwrapper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.originmc.cannondebug.CannonDebugRebornPlugin;
import xyz.fragmentmc.entropy.event.EntityMergeEvent;

public class EntityMergeListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityMerge(EntityMergeEvent event) {
        if(CannonDebugRebornPlugin.INSTANCE.getTrackedEntities().contains(event.getMergeDeleteEntity().getEntityId())) event.setCancelled(true);
    }
}
