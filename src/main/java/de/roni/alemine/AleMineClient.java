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
 * Befehle: /alemine start <länge> <breite> <tiefe> · /alemine xp · /alemine pause | stop | status
 * Tasten (änderbar unter Optionen → Steuerung → AleMine): M = Menü, K = Pause/Weiter, J = Stopp
 */
public class AleMineClient implements ClientModInitializer {

	public static Task job;

	private static KeyMapping menuKey;
	private static KeyMapping pauseKey;
	private static KeyMapping stopKey;
	private static boolean openMenuNextTick = false;

	@Override
	public void onInitializeClient() {
		AleMineConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("alemine", "alemine"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.menu", SDLScancode.SDL_SCANCODE_M, category));
		pauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.pause", SDLScancode.SDL_SCANCODE_K, category));
		stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.stop", SDLScancode.SDL_SCANCODE_J, category));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("alemine")
						.then(ClientCommands.literal("start")
								.executes(ctx -> {
									startMining(AleMineConfig.length, AleMineConfig.width, AleMineConfig.depth);
									return 1;
								})
								.then(ClientCommands.argument("laenge", IntegerArgumentType.integer(1, 128))
										.then(ClientCommands.argument("breite", IntegerArgumentType.integer(1, 128))
												.then(ClientCommands.argument("tiefe", IntegerArgumentType.integer(1, 320))
														.executes(ctx -> {
															AleMineConfig.length = IntegerArgumentType.getInteger(ctx, "laenge");
															AleMineConfig.width = IntegerArgumentType.getInteger(ctx, "breite");
															AleMineConfig.depth = IntegerArgumentType.getInteger(ctx, "tiefe");
															AleMineConfig.save();
															startMining(AleMineConfig.length, AleMineConfig.width, AleMineConfig.depth);
															return 1;
														})))))
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
							say("Menü: Taste M  ·  /alemine start <länge> <breite> <tiefe>  ·  /alemine xp  ·  /alemine pause | stop | status", ChatFormatting.YELLOW);
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

			if (job == null) return;
			if (client.player == null || client.level == null || client.gameMode == null) {
				job = null; // Welt verlassen
				return;
			}
			job.tick(client);
			if (job != null && job.isFinished()) job = null;
		});
	}

	public static void startMining(int length, int width, int depth) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (job != null) job.cancel(mc);
		MiningJob m = new MiningJob(mc, length, width, depth);
		job = m;
		say("Graben: " + length + " lang × " + width + " breit × " + depth + " tief. Pause: K · Stopp: J · Menü: M", ChatFormatting.GREEN);
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
