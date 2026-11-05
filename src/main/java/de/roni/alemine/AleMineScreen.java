package de.roni.alemine;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Menü (Taste M): Graben-Einstellungen mit Live-Zählung und XP-Farm-Modus. */
public class AleMineScreen extends Screen {

	public enum Mode { MINE, XP }

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int GOLD = 0xFFFFAA00;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;

	private static Mode lastMode = Mode.MINE;

	private final Mode mode;
	private String scanKey = "";
	private AreaScan scan;
	private int rowY0;

	public AleMineScreen() {
		this(lastMode);
	}

	public AleMineScreen(Mode mode) {
		super(Component.literal("AleMine"));
		this.mode = mode;
		lastMode = mode;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int top = Math.max(8, this.height / 2 - 120);
		rowY0 = top + 40;

		// Modus-Tabs
		Button mineTab = addRenderableWidget(Button.builder(Component.literal("⛏ Graben"), b -> switchTo(Mode.MINE))
				.pos(cx - 102, top + 14).size(100, 20).build());
		Button xpTab = addRenderableWidget(Button.builder(Component.literal("⚔ XP-Farm"), b -> switchTo(Mode.XP))
				.pos(cx + 2, top + 14).size(100, 20).build());
		mineTab.active = mode != Mode.MINE;
		xpTab.active = mode != Mode.XP;

		if (mode == Mode.MINE) {
			stepperRow(0, () -> AleMineConfig.length, v -> AleMineConfig.length = AleMineConfig.clamp(v, 1, 128), 1, 5);
			stepperRow(1, () -> AleMineConfig.width, v -> AleMineConfig.width = AleMineConfig.clamp(v, 1, 128), 1, 5);
			stepperRow(2, () -> AleMineConfig.depth, v -> AleMineConfig.depth = AleMineConfig.clamp(v, 1, 320), 1, 5);
			addRenderableWidget(Button.builder(waterLabel(), b -> {
						AleMineConfig.stopOnWater = !AleMineConfig.stopOnWater;
						b.setMessage(waterLabel());
						AleMineConfig.save();
					})
					.pos(cx - 100, rowY0 + 3 * 24).size(200, 20)
					.tooltip(Tooltip.create(Component.literal("Lava stoppt immer. Wasser kann man hier ausschalten.")))
					.build());
		} else {
			stepperRow(0, () -> AleMineConfig.hitInterval,
					v -> AleMineConfig.hitInterval = AleMineConfig.clamp(v, AleMineConfig.MIN_HIT_INTERVAL, AleMineConfig.MAX_HIT_INTERVAL), 1, 10);
			addRenderableWidget(Button.builder(healthLabel(), b -> {
						AleMineConfig.xpStopOnLowHealth = !AleMineConfig.xpStopOnLowHealth;
						b.setMessage(healthLabel());
						AleMineConfig.save();
					})
					.pos(cx - 100, rowY0 + 24).size(200, 20).build());
		}

		// untere Leiste
		int by = this.height - 28;
		addRenderableWidget(Button.builder(Component.literal("▶ Start"), b -> {
					AleMineConfig.save();
					this.onClose();
					if (mode == Mode.MINE) AleMineClient.startMining(AleMineConfig.length, AleMineConfig.width, AleMineConfig.depth);
					else AleMineClient.startXpFarm();
				})
				.pos(cx - 154, by).size(74, 20).build());
		addRenderableWidget(Button.builder(Component.literal("⏸ Pause"), b -> AleMineClient.togglePause())
				.pos(cx - 76, by).size(74, 20).build());
		addRenderableWidget(Button.builder(Component.literal("⏹ Stopp"), b -> AleMineClient.stop("Gestoppt."))
				.pos(cx + 2, by).size(74, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
				.pos(cx + 80, by).size(74, 20).build());
	}

	/** Eine Zeile: [−groß][−klein]  Wert  [+klein][+groß] */
	private void stepperRow(int row, IntSupplier get, IntConsumer set, int small, int big) {
		int cx = this.width / 2;
		int y = rowY0 + row * 24;
		addRenderableWidget(Button.builder(Component.literal("−" + big), b -> change(get, set, -big)).pos(cx - 10, y).size(26, 20).build());
		addRenderableWidget(Button.builder(Component.literal("−" + small), b -> change(get, set, -small)).pos(cx + 18, y).size(22, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+" + small), b -> change(get, set, small)).pos(cx + 96, y).size(22, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+" + big), b -> change(get, set, big)).pos(cx + 120, y).size(26, 20).build());
	}

	private static void change(IntSupplier get, IntConsumer set, int delta) {
		set.accept(get.getAsInt() + delta);
		AleMineConfig.save();
	}

	private void switchTo(Mode m) {
		if (this.minecraft != null) this.minecraft.gui.setScreen(new AleMineScreen(m));
	}

	private static Component waterLabel() {
		return Component.literal("Bei Wasser stoppen: " + (AleMineConfig.stopOnWater ? "An" : "Aus"));
	}

	private static Component healthLabel() {
		return Component.literal("Bei wenig Leben stoppen: " + (AleMineConfig.xpStopOnLowHealth ? "An" : "Aus"));
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		int cx = this.width / 2;
		int top = Math.max(8, this.height / 2 - 120);
		int labelX = cx - 150;
		g.centeredText(this.font, "AleMine – alle meine Blöcke", cx, top, GOLD);

		if (mode == Mode.MINE) drawMine(g, cx, labelX);
		else drawXp(g, cx, labelX);

		// Status des laufenden Auftrags
		Task job = AleMineClient.job;
		String status = job == null ? "Kein Auftrag aktiv." : "Läuft: " + job.statusText();
		g.centeredText(this.font, status, cx, this.height - 42, job == null ? GRAY : GREEN);
	}

	private void drawMine(GuiGraphicsExtractor g, int cx, int labelX) {
		row(g, 0, labelX, cx, "Länge (vorwärts)", String.valueOf(AleMineConfig.length));
		row(g, 1, labelX, cx, "Breite (rechts)", String.valueOf(AleMineConfig.width));
		row(g, 2, labelX, cx, "Tiefe (runter)", String.valueOf(AleMineConfig.depth));

		LocalPlayer p = this.minecraft == null ? null : this.minecraft.player;
		if (p == null || this.minecraft.level == null) return;

		BlockPos origin = p.blockPosition();
		Direction fwd = MiningJob.horizontalFromYaw(p.getYRot());
		String key = origin.asLong() + "|" + fwd + "|" + AleMineConfig.length + "|" + AleMineConfig.width + "|" + AleMineConfig.depth;
		if (!key.equals(scanKey) || scan == null) {
			scanKey = key;
			scan = AreaScan.scan(this.minecraft.level, origin, fwd, AleMineConfig.length, AleMineConfig.width, AleMineConfig.depth);
		}

		int y = rowY0 + 4 * 24 + 4;
		g.centeredText(this.font, "Bereich: " + AleMineConfig.length + " × " + AleMineConfig.width + " × " + AleMineConfig.depth
				+ " = " + fmt(scan.total()) + " Blöcke", cx, y, WHITE);
		y += 12;
		if (scan.tooBig()) {
			g.centeredText(this.font, "Zu groß zum Live-Zählen", cx, y, GRAY);
		} else {
			g.centeredText(this.font, "Abzubauen: " + fmt(scan.toMine()) + "  ·  schon Luft: " + fmt(scan.empty()), cx, y, GREEN);
			y += 12;
			boolean warn = scan.lava() > 0 || scan.unbreakable() > 0 || (AleMineConfig.stopOnWater && scan.water() > 0);
			String w = "Lava: " + scan.lava() + "  ·  Wasser: " + scan.water() + "  ·  Bedrock: " + scan.unbreakable();
			g.centeredText(this.font, (warn ? "⚠ " : "") + w, cx, y, warn ? RED : GRAY);
		}
		y += 12;
		g.centeredText(this.font, "Richtung: " + dirName(fwd) + "  ·  Start bei " + origin.toShortString()
				+ "  (Blickrichtung zählt)", cx, y, GRAY);
	}

	private void drawXp(GuiGraphicsExtractor g, int cx, int labelX) {
		double sec = AleMineConfig.hitInterval / 20.0;
		row(g, 0, labelX, cx, "Schlag-Abstand", String.format("%.2f s", sec));

		int y = rowY0 + 2 * 24 + 4;
		Entity target = this.minecraft == null ? null : XpFarmJob.currentTarget(this.minecraft);
		g.centeredText(this.font, "Ziel im Fadenkreuz: " + (target == null ? "keins" : target.getName().getString()),
				cx, y, target == null ? YELLOW : GREEN);
		y += 12;
		g.centeredText(this.font, "Schlägt nur, was du anvisierst. Menü schließen, Ziel anschauen, fertig.", cx, y, GRAY);
		y += 12;
		g.centeredText(this.font, "Mind. 0,5 s Abstand – sonst geht ein Rüstungsständer kaputt.", cx, y, GRAY);
		if (this.minecraft != null && this.minecraft.player != null) {
			y += 12;
			g.centeredText(this.font, "Level jetzt: " + this.minecraft.player.experienceLevel, cx, y, WHITE);
		}
	}

	private void row(GuiGraphicsExtractor g, int row, int labelX, int cx, String label, String value) {
		int y = rowY0 + row * 24 + 6;
		g.text(this.font, label, labelX, y, WHITE);
		g.centeredText(this.font, value, cx + 68, y, GOLD);
	}

	private static String fmt(int n) {
		return String.format("%,d", n).replace(',', '.');
	}

	private static String dirName(Direction d) {
		return switch (d) {
			case NORTH -> "Norden";
			case SOUTH -> "Süden";
			case EAST -> "Osten";
			case WEST -> "Westen";
			default -> d.toString();
		};
	}
}
