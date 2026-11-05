package de.roni.alemine;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

/**
 * AleMine – reiner Client-Mod.
 * Menü: Taste M oder /alemine menu
 * Befehle: /alemine start <länge> <breite> <tiefe> · /alemine tunnel <l> <b> <höhe> · /alemine pos1|pos2|markiert|clear · /alemine xp · /alemine pause | stop | status
 * Tasten (änderbar unter Optionen → Steuerung → AleMine): M = Menü, K = Pause/Weiter, J = Stopp
 */
public class AleMineClient implements ClientModInitializer {

	public static Task job;

	private static KeyMapping menuKey;
	private static KeyMapping pauseKey;
	private static KeyMapping stopKey;
	private static KeyMapping pos1Key;
	private static KeyMapping pos2Key;
	private static KeyMapping previewKey;
	private static KeyMapping chestKey;
	private static boolean openMenuNextTick = false;

	@Override
	public void onInitializeClient() {
		AleMineConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("alemine", "alemine"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.menu", SDLScancode.SDL_SCANCODE_M, category));
		pauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.pause", SDLScancode.SDL_SCANCODE_K, category));
		stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.stop", SDLScancode.SDL_SCANCODE_J, category));
		pos1Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.pos1", SDLScancode.SDL_SCANCODE_COMMA, category));
		pos2Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.pos2", SDLScancode.SDL_SCANCODE_PERIOD, category));
		previewKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.preview", SDLScancode.SDL_SCANCODE_V, category));
		chestKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.chest", SDLScancode.SDL_SCANCODE_H, category));
		Preview.register();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("alemine")
						.then(ClientCommands.literal("start")
								.executes(ctx -> {
									startPlanned();
									return 1;
								})
								.then(ClientCommands.argument("laenge", IntegerArgumentType.integer(1, 128))
										.then(ClientCommands.argument("breite", IntegerArgumentType.integer(1, 128))
												.then(ClientCommands.argument("tiefe", IntegerArgumentType.integer(1, 320))
														.executes(ctx -> {
															AleMineConfig.length = IntegerArgumentType.getInteger(ctx, "laenge");
															AleMineConfig.width = IntegerArgumentType.getInteger(ctx, "breite");
															AleMineConfig.depth = IntegerArgumentType.getInteger(ctx, "tiefe");
															AleMineConfig.mineMode = Area.Mode.DOWN;
															AleMineConfig.save();
															startPlanned();
															return 1;
														})))))
						.then(ClientCommands.literal("tunnel")
								.then(ClientCommands.argument("laenge", IntegerArgumentType.integer(1, 128))
										.then(ClientCommands.argument("breite", IntegerArgumentType.integer(1, 128))
												.then(ClientCommands.argument("hoehe", IntegerArgumentType.integer(2, MiningJob.MAX_ABOVE_FEET + 1))
														.executes(ctx -> {
															AleMineConfig.length = IntegerArgumentType.getInteger(ctx, "laenge");
															AleMineConfig.width = IntegerArgumentType.getInteger(ctx, "breite");
															AleMineConfig.height = IntegerArgumentType.getInteger(ctx, "hoehe");
															AleMineConfig.mineMode = Area.Mode.FORWARD;
															AleMineConfig.save();
															startPlanned();
															return 1;
														})))))
						.then(ClientCommands.literal("pos1").executes(ctx -> {
							Selection.set(1);
							return 1;
						}))
						.then(ClientCommands.literal("pos2").executes(ctx -> {
							Selection.set(2);
							return 1;
						}))
						.then(ClientCommands.literal("markiert").executes(ctx -> {
							AleMineConfig.mineMode = Area.Mode.MARKED;
							startPlanned();
							return 1;
						}))
						.then(ClientCommands.literal("kiste").executes(ctx -> {
							Storage.setChestFromAim();
							return 1;
						}))
						.then(ClientCommands.literal("item").executes(ctx -> {
							Storage.addHeldItem();
							return 1;
						}))
						.then(ClientCommands.literal("clear").executes(ctx -> {
							Selection.clear();
							return 1;
						}))
						.then(ClientCommands.literal("xp").executes(ctx -> {
							startXpFarm();
							return 1;
						}))
						.then(ClientCommands.literal("menu").executes(ctx -> {
							openMenuNextTick = true; // erst nach dem Schließen des Chats öffnen
							return 1;
						}))
						.then(ClientCommands.literal("pause").executes(ctx -> {
							togglePause();
							return 1;
						}))
						.then(ClientCommands.literal("stop").executes(ctx -> {
							stop("Gestoppt.");
							return 1;
						}))
						.then(ClientCommands.literal("status").executes(ctx -> {
							say(job == null ? "Kein Auftrag aktiv." : job.statusText(), ChatFormatting.GRAY);
							return 1;
						}))
						.executes(ctx -> {
							say("Menü: Taste M  ·  /alemine start <l> <b> <tiefe>  ·  /alemine tunnel <l> <b> <höhe>  ·  /alemine pos1 | pos2 | markiert | clear  ·  /alemine kiste | item  ·  /alemine xp  ·  /alemine pause | stop | status", ChatFormatting.YELLOW);
							return 1;
						})));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openMenuNextTick && client.gui.screen() == null) {
				openMenuNextTick = false;
				client.gui.setScreen(new AleMineScreen());
			}
			while (menuKey.consumeClick()) {
				if (client.player != null) client.gui.setScreen(new AleMineScreen());
			}
			while (pauseKey.consumeClick()) togglePause();
			while (stopKey.consumeClick()) stop("Gestoppt.");
			while (chestKey.consumeClick()) Storage.setChestFromAim();
			while (pos1Key.consumeClick()) Selection.set(1);
			while (pos2Key.consumeClick()) Selection.set(2);
			while (previewKey.consumeClick()) {
				AleMineConfig.preview = !AleMineConfig.preview;
				AleMineConfig.save();
				say("Vorschau " + (AleMineConfig.preview ? "an" : "aus") + ".", ChatFormatting.GRAY);
			}

			if (job == null) return;
			if (client.player == null || client.level == null || client.gameMode == null) {
				job = null; // Welt verlassen
				return;
			}
			job.tick(client);
			if (job != null && job.isFinished()) job = null;
		});
	}

	/** Startet den Bereich, den das Menü gerade zeigt (Runter / Nach vorne / Markiert). */
	public static void startPlanned() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Area area = Preview.plannedArea();
		if (area == null) {
			say("Erst Ecke 1 und Ecke 2 markieren (Tasten , und . oder /alemine pos1 / pos2).", ChatFormatting.YELLOW);
			return;
		}
		MiningJob m = new MiningJob(mc, area);
		if (m.error() != null) {
			say(m.error(), ChatFormatting.YELLOW);
			return;
		}
		if (job != null) job.cancel(mc);
		job = m;
		say("Graben: " + area.sizeText() + " = " + area.volume() + " Blöcke. Pause: K · Stopp: J · Menü: M", ChatFormatting.GREEN);
	}

	public static void startXpFarm() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (job != null) job.cancel(mc);
		job = new XpFarmJob();
		say("XP-Farm: schlage alle " + String.format("%.2f", AleMineConfig.hitInterval / 20.0)
				+ " s auf das Ziel im Fadenkreuz. Pause: K · Stopp: J", ChatFormatting.GREEN);
	}

	public static void togglePause() {
		if (job == null) {
			say("Kein Auftrag aktiv.", ChatFormatting.GRAY);
			return;
		}
		job.togglePause(Minecraft.getInstance());
		say(job.isPaused() ? "Pausiert." : "Weiter geht's.", ChatFormatting.YELLOW);
	}

	public static void stop(String reason) {
		if (job == null) {
			say("Kein Auftrag aktiv.", ChatFormatting.GRAY);
			return;
		}
		job.cancel(Minecraft.getInstance());
		job = null;
		say(reason, ChatFormatting.RED);
	}

	static void say(String text, ChatFormatting color) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[AleMine] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(text).withStyle(color)));
		}
	}
}
