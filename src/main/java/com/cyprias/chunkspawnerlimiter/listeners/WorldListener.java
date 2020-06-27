package com.cyprias.chunkspawnerlimiter.listeners;

import com.cyprias.chunkspawnerlimiter.ChunkSpawnerLimiterPlugin;
import java.util.HashMap;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class WorldListener implements Listener {

	private final ChunkSpawnerLimiterPlugin plugin;
	private final HashMap<Chunk, BukkitTask> chunkTasks;

	public WorldListener(@NotNull ChunkSpawnerLimiterPlugin plugin) {
		this.plugin = plugin;
		this.chunkTasks = new HashMap<>();
	}

	private class InspectTask extends BukkitRunnable {
		private final Chunk chunk;

		InspectTask(final @NotNull Chunk chunk) {
			this.chunk = chunk;
		}

		@Override
		public void run() {
			plugin.debug(() -> "Active check " + chunk.getX() + " " + chunk.getZ());
			if (!chunk.isLoaded()) {
				chunkTasks.remove(chunk);
				this.cancel();
				return;
			}
			plugin.checkChunk(chunk, null);
		}
	}

	@EventHandler
	public void onChunkLoadEvent(final @NotNull ChunkLoadEvent event) {
		plugin.debug(() -> "ChunkLoadEvent " + event.getChunk().getX() + " " + event.getChunk().getZ());
		if (plugin.getConfig().getBoolean("properties.active-inspections")) {
			BukkitTask task = new InspectTask(event.getChunk()).runTaskTimer(plugin, 0,
					plugin.getConfig().getInt("properties.inspection-frequency") * 20L);

			chunkTasks.put(event.getChunk(), task);
		} else if (plugin.getConfig().getBoolean("properties.check-chunk-load")) {
			// Active inspection will check immediately as well, no need to check twice
			plugin.getServer().getScheduler().runTask(plugin, () -> plugin.checkChunk(event.getChunk(), null));
		}
	}

	@EventHandler
	public void onChunkUnloadEvent(final @NotNull ChunkUnloadEvent event) {
		plugin.debug(() -> "ChunkUnloadEvent " + event.getChunk().getX() + " " + event.getChunk().getZ());

		if (chunkTasks.containsKey(event.getChunk())) {
			chunkTasks.remove(event.getChunk()).cancel();
		}

		if (plugin.getConfig().getBoolean("properties.check-chunk-unload")) {
			plugin.checkChunk(event.getChunk(), null);
		}
	}

	public void cancelAllTasks() {
		for (BukkitTask task : chunkTasks.values()) {
			task.cancel();
		}
		chunkTasks.clear();
	}

}
