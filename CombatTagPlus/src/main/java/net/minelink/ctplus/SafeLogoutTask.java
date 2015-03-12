package net.minelink.ctplus;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.NumberConversions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.ChatColor.*;

public final class SafeLogoutTask extends BukkitRunnable {

    private final static Map<UUID, SafeLogoutTask> tasks = new HashMap<>();

    private final CombatTagPlus plugin;

    private final UUID playerId;

    private final Location loc;

    private final long logoutTime;

    private int remainingSeconds = Integer.MAX_VALUE;

    private boolean finished;

    SafeLogoutTask(CombatTagPlus plugin, Player player, long logoutTime) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.loc = player.getLocation();
        this.logoutTime = logoutTime;
    }

    private int getRemainingSeconds() {
        long currentTime = System.currentTimeMillis();
        return logoutTime > currentTime ? NumberConversions.ceil((logoutTime - currentTime) / 1000D) : 0;
    }

    @Override
    public void run() {
        // Cancel the task if player is no longer online
        Player player = plugin.getPlayer(playerId);
        if (player == null) {
            cancel();
            return;
        }

        // Cancel the task if player has moved
        if (hasMoved(player)) {
            player.sendMessage(RED + "Logout cancelled due to movement.");
            cancel();
            return;
        }

        // Safely logout the player once timer is up
        int remainingSeconds = getRemainingSeconds();
        if (remainingSeconds <= 0) {
            finished = true;
            plugin.getTagManager().untag(playerId);
            player.kickPlayer(GREEN + "You were logged out safely.");
            cancel();
            return;
        }

        // Inform player
        if (remainingSeconds < this.remainingSeconds) {
            String remaining = DurationUtils.format(remainingSeconds);
            player.sendMessage(GRAY + "Logging out safely in " + AQUA + remaining + GRAY + " ...");
            this.remainingSeconds = remainingSeconds;
        }
    }

    private boolean hasMoved(Player player) {
        Location l = player.getLocation();
        return loc.getWorld() != l.getWorld() || loc.getBlockX() != l.getBlockX() ||
                loc.getBlockY() != l.getBlockY() || loc.getBlockZ() != l.getBlockZ();
    }

    static void run(CombatTagPlus plugin, Player player) {
        // Do nothing if player already has a task
        if (hasTask(player)) return;

        // Calculate logout time
        long logoutTime = System.currentTimeMillis() + (plugin.getSettings().getLogoutWaitTime() * 1000);

        // Run the task every few ticks for accuracy
        SafeLogoutTask task = new SafeLogoutTask(plugin, player, logoutTime);
        task.runTaskTimer(plugin, 0, 5);

        // Cache the task
        tasks.put(player.getUniqueId(), task);
    }

    static boolean hasTask(Player player) {
        SafeLogoutTask task = tasks.get(player.getUniqueId());
        if (task == null) return false;

        BukkitScheduler s = Bukkit.getScheduler();
        if (s.isQueued(task.getTaskId()) || s.isCurrentlyRunning(task.getTaskId())) {
            return true;
        }

        tasks.remove(player.getUniqueId());
        return false;
    }

    static boolean isFinished(Player player) {
        return hasTask(player) && tasks.get(player.getUniqueId()).finished;
    }

    static boolean cancel(Player player) {
        // Do nothing if player has no logout task
        if (!hasTask(player)) return false;

        // Cancel logout task
        Bukkit.getScheduler().cancelTask(tasks.get(player.getUniqueId()).getTaskId());

        // Remove task early to prevent exploits
        tasks.remove(player.getUniqueId());

        return true;
    }

    static void purgeFinished() {
        Iterator<SafeLogoutTask> iterator = tasks.values().iterator();
        BukkitScheduler s = Bukkit.getScheduler();

        // Loop over each task
        while (iterator.hasNext()) {
            int taskId = iterator.next().getTaskId();

            // Remove entry if task isn't running anymore
            if (!s.isQueued(taskId) && !s.isCurrentlyRunning(taskId)) {
                iterator.remove();
            }
        }
    }

}
