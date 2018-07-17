package com.cyprias.chunkspawnerlimiter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import com.cyprias.chunkspawnerlimiter.listeners.EntityListener;
import com.cyprias.chunkspawnerlimiter.listeners.WorldListener;

public class ChunkSpawnerLimiterPlugin extends JavaPlugin {

	private List<String> ignoreMetadata, excludedWorlds;
	private EntityListener entityListener;
	private WorldListener worldListener;

	@Override
	public void onEnable() {

		// Save default config if it does not exist.
		saveDefaultConfig();

		// Warn console if config is missing properties.
		checkForMissingProperties();

		// Register our event listeners.
		if (getConfig().getBoolean("properties.watch-creature-spawns")) {
			// Only register events once in the event of a config reload.
			if (entityListener == null) {
				entityListener = new EntityListener(this);
				getServer().getPluginManager().registerEvents(entityListener, this);
			}
		} else if (entityListener != null) {
			// Disable listeners that are not enabled when configuration is reloaded.
			HandlerList.unregisterAll(entityListener);
			entityListener = null;
		}
		if (getConfig().getBoolean("properties.active-inspections")
				|| getConfig().getBoolean("properties.check-chunk-load")
				|| getConfig().getBoolean("properties.check-chunk-unload")) {
			if (worldListener == null) {
				worldListener = new WorldListener(this);
				getServer().getPluginManager().registerEvents(worldListener, this);
			}
		} else if (worldListener != null) {
			HandlerList.unregisterAll(worldListener);
			worldListener.cancelAllTasks();
			worldListener = null;
		}

		// Warn if no listeners are enabled.
		if (entityListener == null && worldListener == null) {
			getLogger().severe("No listeners are enabled, the plugin will do nothing!");
			getLogger().severe("Enable creature spawn monitoring, active inspections, or chunk load inspections.");
			getLogger().severe("Edit your configuration and then run '/csl reload'");
		}

		ignoreMetadata = getConfig().getStringList("properties.ignore-metadata");
		excludedWorlds = getConfig().getStringList("excluded-worlds");

	}

	@Override
	public void onDisable() {
		getServer().getScheduler().cancelTasks(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
			this.reloadConfig();
			this.onEnable();
			return true;
		}
		return false;
	}

	private void checkForMissingProperties() {
		File configFile = new File(getDataFolder(), "config.yml");
		if (!configFile.exists()) {
			getLogger().severe("Config file does not exist! Using defaults for all values.");
			return;
		}
		YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(configFile);
		BufferedReader buffered = new BufferedReader(new InputStreamReader(getResource("config.yml")));
		YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(buffered);
		try {
			buffered.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (String property : defaultConfig.getKeys(true)) {
			if (!diskConfig.contains(property)) {
				getLogger().warning(property + " is missing from your config.yml, using default.");
			}
		}
	}

	public boolean checkChunk(Chunk chunk, Entity entity) {
		// Stop processing quickly if this world is excluded from limits.
		if (excludedWorlds.contains(chunk.getWorld().getName())) {
			return false;
		}

		if (entity != null) {
			// Quick return conditions for cancelling new spawns
			if (entity instanceof HumanEntity) {
				return false;
			}

			for (String metadata : ignoreMetadata) {
				if (entity.hasMetadata(metadata)) {
					return false;
				}
			}
		}

		Entity[] entities = chunk.getEntities();
		HashMap<String, ArrayList<Entity>> types = new HashMap<String, ArrayList<Entity>>();

		nextChunkEntity: for (int i = entities.length - 1; i >= 0; i--) {
			Entity chunkEntity = entities[i];
			// Don't include HumanEntities in our summed list at all.
			// They're either Players or plugin-added and we probably shouldn't touch them.
			if (chunkEntity instanceof HumanEntity) {
				continue;
			}

			// Ignore any Entity with listed metadata.
			for (String metadata : ignoreMetadata) {
				if (chunkEntity.hasMetadata(metadata)) {
					continue nextChunkEntity;
				}
			}

			String eType = chunkEntity.getType().name();
			String eGroup = getMobGroup(chunkEntity);

			if (getConfig().contains("entities." + eType)) {
				if (!types.containsKey(eType)) {
					types.put(eType, new ArrayList<Entity>());
				}
				types.get(eType).add(chunkEntity);
			}

			if (getConfig().contains("entities." + eGroup)) {
				if (!types.containsKey(eGroup)) {
					types.put(eGroup, new ArrayList<Entity>());
				}
				types.get(eGroup).add(chunkEntity);
			}
		}

		if (entity != null) {

			String eType = entity.getType().name();

			if (getConfig().contains("entities." + eType)) {
				int typeCount;
				if (types.containsKey(eType)) {
					typeCount = types.get(eType).size() + 1;
				} else {
					typeCount = 1;
				}
				if (typeCount > getConfig().getInt("entities." + eType)) {
					return true;
				}
			}

			String eGroup = getMobGroup(entity);

			if (getConfig().contains("entities." + eGroup)) {
				int typeCount;
				if (types.containsKey(eGroup)) {
					typeCount = types.get(eGroup).size() + 1;
				} else {
					typeCount = 1;
				}
				return typeCount > getConfig().getInt("entities." + eGroup);
			}

		}

		for (Entry<String, ArrayList<Entity>> entry : types.entrySet()) {

			String eType = entry.getKey();
			int limit = getConfig().getInt("entities." + eType);

			if (entry.getValue().size() < limit) {
				continue;
			}

			debug("Removing " + (entry.getValue().size() - limit) + " " + eType + " @ "
					+ chunk.getX() + " " + chunk.getZ());

			if (getConfig().getBoolean("properties.notify-players")) {
				String notification = String.format(ChatColor.translateAlternateColorCodes('&',
						getConfig().getString("messages.removedEntites")),
						entry.getValue().size() - limit, eType);
				for (int i = entities.length - 1; i >= 0; i--) {
					if (entities[i] instanceof Player) {
						((Player) entities[i]).sendMessage(notification);
					}
				}
			}

			boolean skipNamed = getConfig().getBoolean("properties.preserve-named-entities");
			int toRemove = entry.getValue().size() - limit;
			int index = entry.getValue().size() - 1;
			while (toRemove > 0 && index >= 0) {
				Entity toCheck = entry.getValue().get(index);
				if (!skipNamed || toCheck.getCustomName() == null
						|| toCheck instanceof LivingEntity
						&& ((LivingEntity) toCheck).getRemoveWhenFarAway()) {
					toCheck.remove();
					--toRemove;
				}
				--index;
			}
			if (toRemove == 0) {
				continue;
			}
			index = entry.getValue().size() - toRemove - 1;
			for (; index < entry.getValue().size(); index++) {
				entry.getValue().get(index).remove();
			}
		}

		return false;
	}

	public void debug(String mess) {
		if (getConfig().getBoolean("properties.debug-messages")) {
			getLogger().info("[Debug] " + mess);
		}
	}

	public static String getMobGroup(Entity entity) {
		// Determine the general group this mob belongs to.
		if (entity instanceof Animals) {
			// Chicken, Cow, MushroomCow, Ocelot, Pig, Sheep, Wolf
			return "ANIMAL";
		}

		if (entity instanceof Monster) {
			// Blaze, CaveSpider, Creeper, Enderman, Giant, PigZombie, Silverfish, Skeleton, Spider,
			// Witch, Wither, Zombie
			return "MONSTER";
		}

		if (entity instanceof Ambient) {
			// Bat
			return "AMBIENT";
		}

		if (entity instanceof WaterMob) {
			// Squid
			return "WATER_MOB";
		}

		if (entity instanceof NPC) {
			// Villager
			return "NPC";
		}

		// Anything else.
		return "OTHER";
	}

}
