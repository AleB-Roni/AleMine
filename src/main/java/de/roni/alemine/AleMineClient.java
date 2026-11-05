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
 * AleMine – reiner Client-Mod. Befehle:
 *   /alemine start <länge> <breite> <tiefe>
 *   /alemine pause | stop | status
 * Tasten (änderbar unter Optionen → Steuerung → AleMine): K = Pause/Weiter, J = Stopp
 */
public class AleMineClient implements ClientModInitializer {

	public static MiningJob job;

	private static KeyMapping pauseKey;
	private static KeyMapping stopKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("alemine", "alemine"));
		pauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.pause", SDLScancode.SDL_SCANCODE_K, category));
		stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alemine.stop", SDLScancode.SDL_SCANCODE_J, category));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("alemine")
						.then(ClientCommands.literal("start")
								.then(ClientCommands.argument("laenge", IntegerArgumentType.integer(1, 128))
										.then(ClientCommands.argument("breite", IntegerArgumentType.integer(1, 128))
												.then(ClientCommands.argument("tiefe", IntegerArgumentType.integer(1, 320))
														.executes(ctx -> {
															start(IntegerArgumentType.getInteger(ctx, "laenge"),
																	IntegerArgumentType.getInteger(ctx, "breite"),
																	IntegerArgumentType.getInteger(ctx, "tiefe"));
															return 1;
														})))))
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
							say("Benutzung: /alemine start <länge> <breite> <tiefe>  ·  /alemine pause | stop | status", ChatFormatting.YELLOW);
							say("Länge = nach vorne (Blickrichtung), Breite = nach rechts, Tiefe = nach unten.", ChatFormatting.GRAY);
							return 1;
						})));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (pauseKey.consumeClick()) togglePause();
			while (stopKey.consumeClick()) stop("Gestoppt.");

			if (job == null) return;
			if (client.player == null || client.level == null || client.gameMode == null) {
				job = null; // Welt verlassen
				return;
			}
			job.tick(client);
			if (job.isFinished()) job = null;
		});
	}

	private static void start(int length, int width, int depth) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (job != null) job.cancel(mc);
		job = new MiningJob(mc, length, width, depth);
		say("Start: " + length + " lang × " + width + " breit × " + depth + " tief (" + job.totalBlocks() + " Blöcke). "
				+ "Pause: K · Stopp: J", ChatFormatting.GREEN);
	}

	private static void togglePause() {
		if (job == null) {
			say("Kein Auftrag aktiv.", ChatFormatting.GRAY);
			return;
		}
		job.togglePause(Minecraft.getInstance());
		say(job.isPaused() ? "Pausiert." : "Weiter geht's.", ChatFormatting.YELLOW);
	}

	static void stop(String reason) {
		if (job != null) job.cancel(Minecraft.getInstance());
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
