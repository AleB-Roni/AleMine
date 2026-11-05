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

/**
 * Menü (Taste M). Der Hintergrund bleibt durchsichtig, damit man die Vorschau
 * in der Welt live sieht, während man Werte ändert.
 */
public class AleMineScreen extends Screen {

	public enum Tab { MINE, XP }

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFBBBBBB;
	private static final int GOLD = 0xFFFFAA00;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int RED = 0xFFFF5555;
	private static final int AQUA = 0xFF55FFFF;

	private static Tab lastTab = Tab.MINE;

	private final Tab tab;
	private String scanKey = "";
	private AreaScan scan;
	private int top;
	private int rowY0;

	public AleMineScreen() {
		this(lastTab);
	}

	public AleMineScreen(Tab tab) {
		super(Component.literal("AleMine"));
		this.tab = tab;
		lastTab = tab;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		top = 6;
		rowY0 = top + 60;

		Button mineTab = addRenderableWidget(Button.builder(Component.literal("⛏ Graben"), b -> reopen(Tab.MINE))
				.pos(cx - 102, top + 12).size(100, 20).build());
		Button xpTab = addRenderableWidget(Button.builder(Component.literal("⚔ XP-Farm"), b -> reopen(Tab.XP))
				.pos(cx + 2, top + 12).size(100, 20).build());
		mineTab.active = tab != Tab.MINE;
		xpTab.active = tab != Tab.XP;

		if (tab == Tab.MINE) initMine(cx);
		else initXp(cx);

		int by = this.height - 26;
		addRenderableWidget(Button.builder(Component.literal("▶ Start"), b -> {
					AleMineConfig.save();
					this.onClose();
					if (tab == Tab.MINE) AleMineClient.startPlanned();
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

	private void initMine(int cx) {
		addRenderableWidget(Button.builder(modeLabel(), b -> {
					Area.Mode[] all = Area.Mode.values();
					AleMineConfig.mineMode = all[(AleMineConfig.mineMode.ordinal() + 1) % all.length];
					AleMineConfig.save();
					reopen(Tab.MINE);
				})
				.pos(cx - 100, top + 36).size(200, 20)
				.tooltip(Tooltip.create(Component.literal(
						"Runter graben: Loch unter dir\nNach vorne: Tunnel/Raum auf deiner Ebene (bis 5 hoch)\nMarkiert: selbst gesetzte Ecken")))
				.build());

		switch (AleMineConfig.mineMode) {
			case DOWN -> {
				stepperRow(0, () -> AleMineConfig.length, v -> AleMineConfig.length = AleMineConfig.clamp(v, 1, 128), 1, 5);
				stepperRow(1, () -> AleMineConfig.width, v -> AleMineConfig.width = AleMineConfig.clamp(v, 1, 128), 1, 5);
				stepperRow(2, () -> AleMineConfig.depth, v -> AleMineConfig.depth = AleMineConfig.clamp(v, 1, 320), 1, 5);
			}
			case FORWARD -> {
				stepperRow(0, () -> AleMineConfig.length, v -> AleMineConfig.length = AleMineConfig.clamp(v, 1, 128), 1, 5);
				stepperRow(1, () -> AleMineConfig.width, v -> AleMineConfig.width = AleMineConfig.clamp(v, 1, 128), 1, 5);
				stepperRow(2, () -> AleMineConfig.height,
						v -> AleMineConfig.height = AleMineConfig.clamp(v, 2, MiningJob.MAX_ABOVE_FEET + 1), 1, 1);
			}
			case MARKED -> {
				int x = cx + 18;
				addRenderableWidget(Button.builder(Component.literal("Ecke 1 = angeschaut"), b -> Selection.set(1))
						.pos(x, rowY0).size(128, 20).build());
				addRenderableWidget(Button.builder(Component.literal("Ecke 2 = angeschaut"), b -> Selection.set(2))
						.pos(x, rowY0 + 22).size(128, 20).build());
				addRenderableWidget(Button.builder(Component.literal("Markierung löschen"), b -> Selection.clear())
						.pos(x, rowY0 + 44).size(128, 20).build());
			}
		}

		int ty = rowY0 + 3 * 22 + 2;
		if (AleMineConfig.mineMode != Area.Mode.MARKED) {
			toggle(cx - 100, ty, () -> "Breite nach: " + (AleMineConfig.leftSide ? "links" : "rechts"),
					() -> AleMineConfig.leftSide = !AleMineConfig.leftSide);
		} else {
			toggle(cx - 100, ty, () -> "Vorschau: " + (AleMineConfig.preview ? "An" : "Aus"),
					() -> AleMineConfig.preview = !AleMineConfig.preview);
		}
		toggle(cx + 2, ty, () -> "Wasser-Stopp: " + (AleMineConfig.stopOnWater ? "An" : "Aus"),
				() -> AleMineConfig.stopOnWater = !AleMineConfig.stopOnWater);
		toggle(cx - 100, ty + 22, () -> "Lücken füllen: " + (AleMineConfig.bridgeGaps ? "An" : "Aus"),
				() -> AleMineConfig.bridgeGaps = !AleMineConfig.bridgeGaps);
		if (AleMineConfig.mineMode != Area.Mode.MARKED) {
			toggle(cx + 2, ty + 22, () -> "Vorschau: " + (AleMineConfig.preview ? "An" : "Aus"),
					() -> AleMineConfig.preview = !AleMineConfig.preview);
		}
	}

	private void initXp(int cx) {
		stepperRow(0, () -> AleMineConfig.hitInterval,
				v -> AleMineConfig.hitInterval = AleMineConfig.clamp(v, AleMineConfig.MIN_HIT_INTERVAL, AleMineConfig.MAX_HIT_INTERVAL), 1, 10);
		toggle(cx - 100, rowY0 + 24, () -> "Bei wenig Leben stoppen: " + (AleMineConfig.xpStopOnLowHealth ? "An" : "Aus"),
				() -> AleMineConfig.xpStopOnLowHealth = !AleMineConfig.xpStopOnLowHealth);
	}

	private void toggle(int x, int y, java.util.function.Supplier<String> label, Runnable flip) {
		int w = (x < this.width / 2 - 50 && tab == Tab.XP) ? 200 : 98;
		addRenderableWidget(Button.builder(Component.literal(label.get()), b -> {
			flip.run();
			b.setMessage(Component.literal(label.get()));
			AleMineConfig.save();
		}).pos(x, y).size(w, 20).build());
	}

	/** Eine Zeile: [−groß][−klein]  Wert  [+klein][+groß] (bei groß == klein nur zwei Knöpfe). */
	private void stepperRow(int row, IntSupplier get, IntConsumer set, int small, int big) {
		int cx = this.width / 2;
		int y = rowY0 + row * 22;
		if (big != small) addRenderableWidget(Button.builder(Component.literal("−" + big), b -> change(get, set, -big)).pos(cx - 10, y).size(26, 20).build());
		addRenderableWidget(Button.builder(Component.literal("−" + small), b -> change(get, set, -small)).pos(cx + 18, y).size(22, 20).build());
		addRenderableWidget(Button.builder(Component.literal("+" + small), b -> change(get, set, small)).pos(cx + 96, y).size(22, 20).build());
		if (big != small) addRenderableWidget(Button.builder(Component.literal("+" + big), b -> change(get, set, big)).pos(cx + 120, y).size(26, 20).build());
	}

	private static void change(IntSupplier get, IntConsumer set, int delta) {
		set.accept(get.getAsInt() + delta);
		AleMineConfig.save();
	}

	private void reopen(Tab t) {
		if (this.minecraft != null) this.minecraft.gui.setScreen(new AleMineScreen(t));
	}

	private static Component modeLabel() {
		return Component.literal("Modus: " + switch (AleMineConfig.mineMode) {
			case DOWN -> "⬇ Runter graben";
			case FORWARD -> "➡ Nach vorne (Tunnel/Raum)";
			case MARKED -> "▣ Markierter Bereich";
		});
	}

	// ------------------------------------------------------------------ Zeichnen

	@Override
	public boolean isPauseScreen() {
		return false; // Welt läuft weiter → Vorschau bleibt live
	}

	/** Kein Abdunkeln/Verwischen → die Vorschau in der Welt bleibt sichtbar. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		int cx = this.width / 2;
		int labelX = cx - 150;
		g.centeredText(this.font, "AleMine – alle meine Blöcke", cx, top, GOLD);

		if (tab == Tab.MINE) drawMine(g, cx, labelX);
		else drawXp(g, cx, labelX);

		Task job = AleMineClient.job;
		String status = job == null ? "Kein Auftrag aktiv." : "Läuft: " + job.statusText();
		g.centeredText(this.font, status, cx, this.height - 38, job == null ? GRAY : GREEN);
	}

	private void drawMine(GuiGraphicsExtractor g, int cx, int labelX) {
		switch (AleMineConfig.mineMode) {
			case DOWN -> {
				row(g, 0, labelX, cx, "Länge (vorwärts)", String.valueOf(AleMineConfig.length));
				row(g, 1, labelX, cx, "Breite (seitlich)", String.valueOf(AleMineConfig.width));
				row(g, 2, labelX, cx, "Tiefe (runter)", String.valueOf(AleMineConfig.depth));
			}
			case FORWARD -> {
				row(g, 0, labelX, cx, "Länge (vorwärts)", String.valueOf(AleMineConfig.length));
				row(g, 1, labelX, cx, "Breite (seitlich)", String.valueOf(AleMineConfig.width));
				row(g, 2, labelX, cx, "Höhe (ab Füßen)", String.valueOf(AleMineConfig.height));
			}
			case MARKED -> {
				g.text(this.font, "Ecke 1: " + str(Selection.pos1()), labelX, rowY0 + 6, AQUA);
				g.text(this.font, "Ecke 2: " + str(Selection.pos2()), labelX, rowY0 + 28, 0xFFFF55FF);
				g.text(this.font, "Tasten: , und .", labelX, rowY0 + 50, GRAY);
			}
		}

		LocalPlayer p = this.minecraft == null ? null : this.minecraft.player;
		if (p == null || this.minecraft.level == null) return;

		Area area = Preview.plannedArea();
		int y = rowY0 + 5 * 22 + 8;
		if (area == null) {
			g.centeredText(this.font, "Schau einen Block an und setz Ecke 1 und Ecke 2.", cx, y, YELLOW);
			return;
		}

		String key = area + "|" + p.blockPosition().asLong();
		if (!key.equals(scanKey) || scan == null) {
			scanKey = key;
			scan = AreaScan.scan(this.minecraft.level, area);
		}

		g.centeredText(this.font, "Bereich: " + area.sizeText() + " = " + fmt(scan.total()) + " Blöcke", cx, y, WHITE);
		y += 11;
		if (scan.tooBig()) {
			g.centeredText(this.font, "Zu groß zum Live-Zählen", cx, y, GRAY);
		} else {
			g.centeredText(this.font, "Abzubauen: " + fmt(scan.toMine()) + "  ·  schon Luft: " + fmt(scan.empty()), cx, y, GREEN);
			y += 11;
			boolean warn = scan.lava() > 0 || scan.unbreakable() > 0 || (AleMineConfig.stopOnWater && scan.water() > 0);
			g.centeredText(this.font, (warn ? "⚠ " : "") + "Lava: " + scan.lava() + "  ·  Wasser: " + scan.water()
					+ "  ·  Bedrock: " + scan.unbreakable(), cx, y, warn ? RED : GRAY);
		}
		y += 11;
		String err = MiningJob.check(p, area);
		if (err == null) {
			Direction fwd = MiningJob.horizontalFromYaw(p.getYRot());
			g.centeredText(this.font, "✔ Startklar · Blick nach " + dirName(fwd) + " · orange = wird abgebaut", cx, y, GREEN);
		} else {
			g.centeredText(this.font, "✖ " + err, cx, y, YELLOW);
		}
	}

	private void drawXp(GuiGraphicsExtractor g, int cx, int labelX) {
		row(g, 0, labelX, cx, "Schlag-Abstand", String.format("%.2f s", AleMineConfig.hitInterval / 20.0));

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
		int y = rowY0 + row * 22 + 6;
		g.text(this.font, label, labelX, y, WHITE);
		g.centeredText(this.font, value, cx + 68, y, GOLD);
	}

	private static String str(BlockPos b) {
		return b == null ? "–" : b.toShortString();
	}

	private static String fmt(long n) {
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
