package com.norcode.bukkit.jukeloop;

import java.util.LinkedHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class LoopingJukebox {
	private final Location location;
	private final JukeLoopPlugin plugin;
	private int startedAt = -1;
	public boolean isDead = false;
	private int chestSlot = -1;
	public static LinkedHashMap<Location, LoopingJukebox> jukeboxMap = new LinkedHashMap<>();

	public static LoopingJukebox getAt(JukeLoopPlugin plugin, Location loc) {
		LoopingJukebox box = null;
		if (jukeboxMap.containsKey(loc)) {
			box = jukeboxMap.get(loc);
		} else {
			box = new LoopingJukebox(plugin, loc);
		}
		if (box.validate()) {
			jukeboxMap.put(loc, box);
			return box;
		}
		return null;
	}

	public Location getLocation() {
		return location;
	}

	public LoopingJukebox(JukeLoopPlugin plugin, Location location) {
		this.location = location;
		this.plugin = plugin;
	}

	public void log(String msg) {
		if (plugin.debugMode) {
			plugin.getLogger().info("[Jukebox@" + location.getWorld().getName() + " " + location.getBlockX()
					+ " " + location.getBlockY() + " " + location.getBlockZ() + "] " + msg);
		}
	}

	public Jukebox getJukebox() {
		try {
			Block block = location.getBlock();
			if (block.getType() != Material.JUKEBOX) {
				return null;
			}
			return (Jukebox) this.location.getBlock().getState();
		} catch (ClassCastException ex) {
			return null;
		}
	}

	public Chest getChest() {
		for (BlockFace f : JukeLoopPlugin.directions) {
			try {
				Block block = location.getBlock().getRelative(f);
				if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
					continue;
				}
				Chest chest = (Chest) block.getState();
				if (!containsRecords(chest.getInventory())) {
					log(chest + " does not contain records. skipping.");
					continue;
				}
				return chest;
			} catch (ClassCastException ex) {
				log(ex.getMessage());
				continue;
			}
		}
		return null;
	}

	public boolean validate() {
		return getJukebox() != null;
	}

	public boolean containsRecords(Inventory inv) {
		for (ItemStack s : inv.getContents()) {
			if (s != null && JukeLoopPlugin.recordDurations.keySet().contains(s.getType())) {
				return true;
			}
		}
		return false;
	}

	public boolean playersNearby() {
		double dist;
		for (Player p : location.getWorld().getPlayers()) {
			dist = getJukebox().getLocation().distanceSquared(p.getLocation());
			if (dist <= 4096) {
				return true;
			}
		}
		return false;
	}

	public void doLoop() {
		Jukebox jukebox = getJukebox();
		if (jukebox == null) {
			this.isDead = true;
			log("doLoop:Died.");
			return;
		}

		if (!getJukebox().isPlaying()) {
			log("doLoop:not playing.");
			return;
		}

		int now = (int) (System.currentTimeMillis() / 1000);
		Material record = jukebox.getPlaying();
		Integer duration = JukeLoopPlugin.recordDurations.get(record);
		if (duration != null) {
			if (now - startedAt > duration) {
				if (!playersNearby()) {
					log("doLoop:No player nearby.");
					return;
				}
				if (!loopFromChest()) {
					log("doLoop:Couldn't put " + record + " anywhere, repeating.");
					jukebox.setPlaying(record);
					onInsert();
					return;
				}
			}
		}
	}

	private boolean loopFromChest() {
		Chest chest = getChest();
		if (chest == null) {
			return false;
		}
		Jukebox box = getJukebox();
		Inventory inv = chest.getInventory();
		int i = chestSlot + 1;
		while (i != chestSlot) {
			if (i > inv.getSize() - 1) {
				i = 0;
			}
			ItemStack s = inv.getItem(i);
			if (s != null && JukeLoopPlugin.recordDurations.containsKey(s.getType())) {
				log("Taking " + s.getType() + " from slot " + i + " of chest@"
						+ chest.getLocation().getBlockX() + "," + chest.getLocation().getBlockY()
						+ "," + chest.getLocation().getBlockZ());
				Material playing = box.getPlaying();
				box.setPlaying(s.getType());
				onInsert();
				inv.setItem(i, new ItemStack(playing));
				chestSlot = i;
				return true;
			}
			++i;
		}
		return true;
	}

	public void onInsert() {
		startedAt = (int) (System.currentTimeMillis() / 1000);
	}

	public void onEject() {
		this.startedAt = -1;
	}
}
