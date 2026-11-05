package de.roni.alemine;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Einstellungen – gespeichert in .minecraft/config/alemine.properties */
public final class AleMineConfig {
	public static Area.Mode mineMode = Area.Mode.DOWN;
	public static int length = 8;
	public static int width = 8;
	public static int depth = 10;
	public static int height = 3;          // für „Nach vorne“: 2–5
	public static boolean leftSide = false; // Breite nach links statt rechts
	public static boolean stopOnWater = true;
	public static boolean bridgeGaps = true;
	public static boolean preview = true;

	/** Abstand zwischen zwei Schlägen in Ticks (20 = 1 Sekunde). Mind. 10, damit ein Rüstungsständer nicht kaputtgeht. */
	public static int hitInterval = 20;
	public static boolean xpStopOnLowHealth = true;

	public static final int MIN_HIT_INTERVAL = 10;
	public static final int MAX_HIT_INTERVAL = 20 * 60;

	private AleMineConfig() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("alemine.properties");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(f)) {
			p.load(r);
			try {
				mineMode = Area.Mode.valueOf(p.getProperty("mineMode", mineMode.name()));
			} catch (IllegalArgumentException ignored) {
			}
			length = clamp(intOf(p, "length", length), 1, 128);
			width = clamp(intOf(p, "width", width), 1, 128);
			depth = clamp(intOf(p, "depth", depth), 1, 320);
			height = clamp(intOf(p, "height", height), 2, MiningJob.MAX_ABOVE_FEET + 1);
			leftSide = bool(p, "leftSide", leftSide);
			stopOnWater = bool(p, "stopOnWater", stopOnWater);
			bridgeGaps = bool(p, "bridgeGaps", bridgeGaps);
			preview = bool(p, "preview", preview);
			hitInterval = clamp(intOf(p, "hitInterval", hitInterval), MIN_HIT_INTERVAL, MAX_HIT_INTERVAL);
			xpStopOnLowHealth = bool(p, "xpStopOnLowHealth", xpStopOnLowHealth);
			Storage.enabled = bool(p, "depositEnabled", Storage.enabled);
			Storage.chestFromString(p.getProperty("chest", ""));
			Storage.itemsFromString(p.getProperty("depositItems", ""));
		} catch (Exception ignored) {
			// kaputte Datei → Standardwerte
		}
	}

	public static void save() {
		Properties p = new Properties();
		p.setProperty("mineMode", mineMode.name());
		p.setProperty("length", String.valueOf(length));
		p.setProperty("width", String.valueOf(width));
		p.setProperty("depth", String.valueOf(depth));
		p.setProperty("height", String.valueOf(height));
		p.setProperty("leftSide", String.valueOf(leftSide));
		p.setProperty("stopOnWater", String.valueOf(stopOnWater));
		p.setProperty("bridgeGaps", String.valueOf(bridgeGaps));
		p.setProperty("preview", String.valueOf(preview));
		p.setProperty("hitInterval", String.valueOf(hitInterval));
		p.setProperty("xpStopOnLowHealth", String.valueOf(xpStopOnLowHealth));
		p.setProperty("depositEnabled", String.valueOf(Storage.enabled));
		p.setProperty("chest", Storage.chestString());
		p.setProperty("depositItems", Storage.itemsString());
		try (Writer w = Files.newBufferedWriter(file())) {
			p.store(w, "AleMine Einstellungen");
		} catch (Exception ignored) {
		}
	}

	private static boolean bool(Properties p, String key, boolean def) {
		return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
	}

	private static int intOf(Properties p, String key, int def) {
		try {
			return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
