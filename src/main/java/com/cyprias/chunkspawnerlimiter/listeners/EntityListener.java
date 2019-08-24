package com.cyprias.chunkspawnerlimiter.listeners;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import com.cyprias.chunkspawnerlimiter.ChunkSpawnerLimiterPlugin;

public class EntityListener implements Listener {

	private final ChunkSpawnerLimiterPlugin plugin;

	public EntityListener(ChunkSpawnerLimiterPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(ignoreCancelled = true)
	public void onCreatureSpawnEvent(CreatureSpawnEvent event) {

		String reason = event.getSpawnReason().toString();

		if (!plugin.getConfig().getBoolean("spawn-reasons." + reason)
				|| !plugin.getConfig().getBoolean("spawn-reasons." + reason)) {
			plugin.debug("Ignoring " + event.getEntity().getType().toString() + " due to spawnreason " + reason);
			return;
		}

		Chunk chunk = event.getLocation().getChunk();

		if (plugin.getConfig().getBoolean("properties.prevent-creature-spawns")) {
			if (plugin.checkChunk(chunk, event.getEntity())) {
				event.setCancelled(true);
			}
			// If we are preventing new spawns instead of culling, don't cull surrounding chunks.
			return;
		}

		int surrounding = plugin.getConfig().getInt("properties.check-surrounding-chunks");
		int x = chunk.getX() - surrounding;
		int z = chunk.getZ() - surrounding;
		int endX = chunk.getX() + surrounding + 1;
		int endZ = chunk.getZ() + surrounding + 1;

		World world = chunk.getWorld();
		for (; x < endX; x++) {
			for (; z < endZ; z++) {
				if (!world.isChunkLoaded(x, z)) {
					continue;
				}
				plugin.checkChunk(world.getChunkAt(x, z), null);
			}
		}
	}

}
