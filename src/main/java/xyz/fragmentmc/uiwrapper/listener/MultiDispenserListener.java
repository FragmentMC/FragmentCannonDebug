package xyz.fragmentmc.uiwrapper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.originmc.cannondebug.CannonDebugRebornPlugin;
import xyz.fragmentmc.fragmentcore.api.events.MultiDispenserEvent;

public class MultiDispenserListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMultiDispenser(MultiDispenserEvent event) {
        if(!event.isCancelled()) CannonDebugRebornPlugin.INSTANCE.getWorldListener().doDispenserTracking(event.getBlock(), false, event.getEntity());
    }
}
