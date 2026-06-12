package com.github.oowjzzoo.magicloot3;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.github.oowjzzoo.magicloot3.dummy.TrainingDummy;
import com.github.oowjzzoo.magicloot3.dummy.TrainingDummyListener;
import com.github.oowjzzoo.magicloot3.machines.EquipmentSplitter;
import com.github.oowjzzoo.magicloot3.machines.LivingDropper;

/**
 * Runs periodic cleanup of slowly-accumulating stale data (1-minute cycle).
 * Only logs when actual cleanup occurs.
 */
public final class Housekeeper {

    private Housekeeper() {}

    public static void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int n = LootListener.cleanupStaleSelfDamageTimers();
            if (n > 0) plugin.getLogger().log(Level.INFO, "Housekeeper: cleaned {0} stale self-damage timers", n);
            n = TrainingDummy.cleanupStaleDummies();
            if (n > 0) plugin.getLogger().log(Level.INFO, "Housekeeper: cleaned {0} stale training dummies", n);
            n = LostLibrarianGUI.cleanupStaleDeskState();
            if (n > 0) plugin.getLogger().log(Level.INFO, "Housekeeper: cleaned {0} stale desk states", n);
            n = LivingDropperListener.cleanupStalePlayerLocs();
            if (n > 0) plugin.getLogger().log(Level.INFO, "Housekeeper: cleaned {0} stale living-dropper locs", n);
            n = EquipmentSplitter.cleanupStaleStates();
            if (n > 0) plugin.getLogger().log(Level.INFO, "Housekeeper: cleaned {0} stale splitter states", n);
            n = TrainingDummyListener.cleanupStaleInteract();
            if (n > 0) plugin.getLogger().log(Level.INFO, "Housekeeper: cleaned {0} stale dummy-interact records", n);
            LivingDropper.saveData();
        }, 1200L, 1200L);
    }
}
