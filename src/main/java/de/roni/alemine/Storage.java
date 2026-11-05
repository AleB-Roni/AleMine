package de.roni.alemine;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Kiste zum Einlagern + Liste der Items, die eingelagert werden sollen. */
public final class Storage {

	/** Max. Abstand Augen → Kistenmitte vom Startpunkt aus. */
	public static final double REACH = 4.4;

	public static BlockPos chest;
	public static boolean enabled = true;
	/** Item-IDs (z. B. minecraft:cobblestone). Leer = alle Blöcke. */
	public static final Set<String> items = new LinkedHashSet<>();

	private Storage() {}

	public static void setChestFromAim() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK) {
			chest = bhr.getBlockPos();
			AleMineConfig.save();
			AleMineClient.say("Kiste = " + chest.toShortString() + " ("
					+ mc.level.getBlockState(chest).getBlock().getName().getString() + "). Muss in Reichweite deines Startpunkts stehen.",
					ChatFormatting.AQUA);
		} else {
			AleMineClient.say("Schau eine Kiste an und drück dann H.", ChatFormatting.YELLOW);
		}
	}

	public static void addHeldItem() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		ItemStack s = p.getMainHandItem();
		if (s.isEmpty()) {
			AleMineClient.say("Nimm erst das Item in die Hand, das eingelagert werden soll.", ChatFormatting.YELLOW);
			return;
		}
		String id = idOf(s.getItem());
		if (items.add(id)) AleMineClient.say("Wird eingelagert: " + s.getHoverName().getString(), ChatFormatting.AQUA);
		else {
			items.remove(id);
			AleMineClient.say("Nicht mehr einlagern: " + s.getHoverName().getString(), ChatFormatting.GRAY);
		}
		AleMineConfig.save();
	}

	public static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static String nameOf(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) return id;
		return BuiltInRegistries.ITEM.getOptional(key).map(i -> i.getDefaultInstance().getHoverName().getString()).orElse(id);
	}

	public static boolean active() {
		return enabled && chest != null;
	}

	/** Darf das raus? (Werkzeuge, Essen usw. bleiben, außer sie stehen ausdrücklich in der Liste.) */
	public static boolean shouldDeposit(ItemStack s) {
		if (s.isEmpty()) return false;
		if (!items.isEmpty()) return items.contains(idOf(s.getItem()));
		return s.getItem() instanceof BlockItem bi && !(bi.getBlock() instanceof EntityBlock);
	}

	/** Hotbar-Slot mit Füllblöcken, der behalten wird (für Lücken und den Turm zurück nach oben). */
	public static int keepSlot(LocalPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < 9; i++) {
			if (MiningJob.isFiller(p, inv.getItem(i))) return i;
		}
		return -1;
	}

	public static boolean inventoryFull(LocalPlayer p) {
		return p.getInventory().getFreeSlot() < 0;
	}

	public static boolean hasDepositables(LocalPlayer p) {
		Inventory inv = p.getInventory();
		int keep = keepSlot(p);
		for (int i = 0; i < 36; i++) {
			if (i != keep && shouldDeposit(inv.getItem(i))) return true;
		}
		return false;
	}

	public static boolean inReachFrom(BlockPos feet) {
		if (chest == null) return false;
		Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
		return eye.distanceTo(Vec3.atCenterOf(chest)) <= REACH;
	}

	// --- Speichern (über AleMineConfig) ---

	static String chestString() {
		return chest == null ? "" : chest.getX() + "," + chest.getY() + "," + chest.getZ();
	}

	static void chestFromString(String s) {
		chest = null;
		if (s == null || s.isBlank()) return;
		String[] a = s.split(",");
		if (a.length != 3) return;
		try {
			chest = new BlockPos(Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()));
		} catch (NumberFormatException ignored) {
		}
	}

	static String itemsString() {
		return String.join(",", items);
	}

	static void itemsFromString(String s) {
		items.clear();
		if (s == null || s.isBlank()) return;
		for (String id : s.split(",")) if (!id.isBlank()) items.add(id.trim());
	}
}
