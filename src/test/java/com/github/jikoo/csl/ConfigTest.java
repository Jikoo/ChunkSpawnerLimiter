package com.github.jikoo.csl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.Test;

import static org.junit.Assert.fail;

public class ConfigTest {

	@Test
	public void testConfig() {
		YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));

		testSpawnReason(configuration);
	}

	public void testSpawnReason(YamlConfiguration configuration) {
		List<String> missing = new ArrayList<>();
		for (CreatureSpawnEvent.SpawnReason reason : CreatureSpawnEvent.SpawnReason.values()) {
			if (!configuration.contains("spawn-reasons." + reason.name())) {
				missing.add(reason.name());
			}
		}

		if (missing.size() > 0) {
			fail("Missing spawn reasons: " + missing);
		}
	}

}
