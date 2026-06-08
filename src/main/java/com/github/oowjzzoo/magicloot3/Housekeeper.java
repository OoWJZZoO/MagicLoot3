package com.github.oowjzzoo.magicloot3;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.github.oowjzzoo.magicloot3.dummy.TrainingDummy;

/**
 * Runs periodic cleanup of slowly-accumulating stale data (1-minute cycle).
 * Only logs when actual cleanup occurs.
 */
public final class Housekeeper {

    private Housekeeper() {}

    public static void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int removed = 0;
            removed += LootListener.cleanupStaleSelfDamageTimers();
            removed += TrainingDummy.cleanupStaleDummies();
            removed += LostLibrarianGUI.cleanupStaleDeskState();
            removed += LivingDropperListener.cleanupStalePlayerLocs();
            if (removed > 0) {
                plugin.getLogger().log(Level.INFO,
                        "Housekeeper: cleaned {0} stale entries", removed);
            }
        }, 1200L, 1200L);
    }
}
