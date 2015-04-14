package com.norcode.bukkit.jukeloop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Attachable;
import org.bukkit.material.Diode;
import org.bukkit.material.PressurePlate;
import org.bukkit.material.Redstone;
import org.bukkit.material.RedstoneTorch;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class JukeLoopPlugin extends JavaPlugin implements Listener {
	private static Pattern locRegex = Pattern.compile("(\\w+)_(\\-?\\d+)_(\\-?\\d+)_(\\-?\\d+)");

	public static BlockFace[] directions = new BlockFace[] { BlockFace.EAST, BlockFace.WEST,
			BlockFace.NORTH, BlockFace.SOUTH, BlockFace.DOWN, BlockFace.UP };

	public static ArrayList<Material> playlistOrder;
	public static HashMap<Material, Integer> recordDurations = new HashMap<Material, Integer>();
	private BukkitTask checkTask = null;
	public boolean debugMode = false;
	Location l;

	private BukkitTask saveTask = null;

	static {
		resetRecordDurations();
	}

	private static void resetRecordDurations() {
		// set record durations
		recordDurations.put(Material.GOLD_RECORD, (2 * 60) + 58);
		recordDurations.put(Material.GREEN_RECORD, (3 * 60) + 5);
		recordDurations.put(Material.RECORD_3, (5 * 60) + 45);
		recordDurations.put(Material.RECORD_4, (3 * 60) + 5);
		recordDurations.put(Material.RECORD_5, (2 * 60) + 54);
		recordDurations.put(Material.RECORD_6, (3 * 60) + 17);
		recordDurations.put(Material.RECORD_7, (1 * 60) + 36);
		recordDurations.put(Material.RECORD_8, (2 * 60) + 30);
		recordDurations.put(Material.RECORD_9, (3 * 60) + 8);
		recordDurations.put(Material.RECORD_10, (4 * 60) + 11);
		recordDurations.put(Material.RECORD_11, (1 * 60) + 11);
		recordDurations.put(Material.RECORD_12, (3 * 60) + 55);
		playlistOrder = new ArrayList<Material>(recordDurations.size());
		for (Material m : recordDurations.keySet()) {
			playlistOrder.add(m);
		}
	}

	void debug(String string) {
		if (getConfig().getBoolean("debug", false)) {
			getLogger().info(string);
		}
	}

	public void loadDurations() {
		ConfigurationSection cfg = getConfig().getConfigurationSection("record-durations");
		for (String key: cfg.getKeys(false)) {
			Material mat = Material.getMaterial(key);
			if (mat == null) {
				getLogger().warning("Unknown record: " + key);
				continue;
			}
			recordDurations.put(mat, cfg.getInt(key));
			if (!playlistOrder.contains(mat)) {
				playlistOrder.add(mat);
			}
		}
		loadData();
		debugMode = getConfig().getBoolean("debug", false);
	}

	@Override
	public void onEnable() {
		saveDefaultConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();
		loadDurations();
		getServer().getPluginManager().registerEvents(this, this);
		checkTask = new BukkitRunnable() {
			@Override
			public void run() {
				LoopingJukebox.jukeboxMap.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isDead);
				LoopingJukebox.jukeboxMap.values().forEach(jukebox -> jukebox.doLoop());
			}
		}.runTaskTimer(this, 40, 40);

		saveTask = new BukkitRunnable() {
			@Override
			public void run() {
				saveData();
			}
		}.runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5);
	}

	private Location parseLocation(String s) {
		Matcher m = locRegex.matcher(s);
		if (m.matches()) {
			try {
				World w = getServer().getWorld(m.group(1));
				if (w == null) {
					w = getServer().createWorld(new WorldCreator(m.group(1)));
				}
				return new Location(w, Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)));
			} catch (IllegalArgumentException ex) {
				getLogger().warning("Invalid entry: " + s);
			}
		}
		return null;
	}

	private void loadData() {
		Location l = null;
		for (String s : getConfig().getStringList("jukeboxes")) {
			l = parseLocation(s);
			LoopingJukebox box = LoopingJukebox.getAt(this, l);
			if (box != null) {
				LoopingJukebox.jukeboxMap.put(l, box);
				debug("initialized " + l + "->" + box);
			}
		}
		debug("map has " + LoopingJukebox.jukeboxMap.size() + " entries.");
	}

	private void saveData() {
		List<String> boxlist = new ArrayList<String>(LoopingJukebox.jukeboxMap.keySet().size());
		for (Location l : LoopingJukebox.jukeboxMap.keySet()) {
			boxlist.add(l.getWorld().getName() + "_" + l.getBlockX() + "_" + l.getBlockY() + "_" + l.getBlockZ());
		}
		getConfig().set("jukeboxes", boxlist);
		saveConfig();
	}

	@Override
	public void onDisable() {
		saveTask.cancel();
		checkTask.cancel();
		saveData();
		super.onDisable();
	}

	@SuppressWarnings("deprecation")
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onBlockRedstoneEvent(BlockRedstoneEvent event) {
		boolean pressed = false;
		switch (event.getBlock().getType()) {
		case REDSTONE_TORCH_OFF:
			RedstoneTorch torch = (RedstoneTorch) event.getBlock().getType().getNewData(event.getBlock().getData());
			for (BlockFace bf : directions) {
				if (bf.equals(torch.getAttachedFace()))
					continue;
				if (event.getBlock().getRelative(bf).getType().equals(Material.JUKEBOX)) {
					loopIfJukebox(event.getBlock().getRelative(bf));
				}
			}
			break;
		case REDSTONE_BLOCK:
		case REDSTONE_WIRE:
			if (event.getOldCurrent() == 0 && event.getNewCurrent() >= 1) {
				for (BlockFace bf : directions) {
					if (event.getBlock().getRelative(bf).getType().equals(Material.JUKEBOX)) {
						loopIfJukebox(event.getBlock().getRelative(bf));
					}
				}
			}
			break;
		case DIODE_BLOCK_OFF:
			Diode diode = (Diode) event.getBlock().getType().getNewData(event.getBlock().getData());
			Block b = event.getBlock().getRelative(diode.getFacing());
			if (b.getType().equals(Material.JUKEBOX)) {
				loopIfJukebox(b);
			}
			break;
		case LEVER:
		case STONE_BUTTON:
		case WOOD_BUTTON:
			Attachable lever = (Attachable) event.getBlock().getType().getNewData(event.getBlock().getData());
			Redstone redstone = (Redstone) event.getBlock().getType().getNewData(event.getBlock().getData());
			if (redstone.isPowered()) {
				for (BlockFace bf : directions) {
					b = event.getBlock().getRelative(bf);
					if (b.getType().equals(Material.JUKEBOX)) {
						loopIfJukebox(b);
					}
				}
				Block other = event.getBlock().getRelative(lever.getAttachedFace());
				for (BlockFace bf : directions) {
					b = other.getRelative(bf);
					if (b.getType().equals(Material.JUKEBOX)) {
						loopIfJukebox(b);
					}
				}
			}
			break;
		case STONE_PLATE:
		case WOOD_PLATE:
			PressurePlate plate = (PressurePlate) event.getBlock().getType().getNewData(event.getBlock().getData());
			pressed = plate.isPressed();
		case GOLD_PLATE:
		case IRON_PLATE:
			if (event.getBlock().getType() == Material.GOLD_PLATE || event.getBlock().getType() == Material.IRON_PLATE) {
				pressed = event.getBlock().getData() == 1;
			}
			if (pressed) {
				for (BlockFace bf : directions) {
					b = event.getBlock().getRelative(bf);
					if (b.getType().equals(Material.JUKEBOX)) {
						loopIfJukebox(b);
					}
				}
				for (BlockFace bf : directions) {
					b = event.getBlock().getRelative(bf);
					if (b.getType().equals(Material.JUKEBOX)) {
						loopIfJukebox(b);
					}
				}
			}
			break;
		case DAYLIGHT_DETECTOR:
			if (event.getBlock().isBlockPowered()) {
				for (BlockFace bf : directions) {
					if (bf.equals(BlockFace.UP))
						continue;
					if (event.getBlock().getRelative(bf).getType().equals(Material.JUKEBOX)) {
						loopIfJukebox(event.getBlock().getRelative(bf));
					}
				}
			}
			break;
		default:
			break;
		}
	}

	public void loopIfJukebox(Block b) {
		LoopingJukebox jb = LoopingJukebox.getAt(this, b.getLocation());
		if (jb != null && jb.getJukebox().isPlaying()) {
			jb.onEject();
			jb.doLoop();
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (label.equals("jukeloop")) {
			if (args.length > 0) {
				if (sender.hasPermission("jukeloop.reload") && args[0].equalsIgnoreCase("reload")) {
					// reload
					reloadConfig();
					resetRecordDurations();
					loadDurations();
					sender.sendMessage("[JukeLoop] Reloaded.");
					return true;
				}
			}
		}
		return false;
	}

	@EventHandler(ignoreCancelled = true)
	public void onInteractJukebox(PlayerInteractEvent e) {
		if (!e.getPlayer().hasPermission("jukeloop.use"))
			return;
		if (e.getAction() == Action.LEFT_CLICK_BLOCK
				&& e.getClickedBlock().getType() == Material.JUKEBOX) {
			if (((Jukebox) e.getClickedBlock().getState()).isPlaying()) {
				LoopingJukebox jb = LoopingJukebox.getAt(this, e.getClickedBlock().getLocation());
				if (jb != null && jb.getJukebox().isPlaying()) {
					jb.onEject();
					jb.doLoop();
					e.setCancelled(true);
				}
			}
		} else if (e.getAction() == Action.RIGHT_CLICK_BLOCK
				&& e.getClickedBlock().getType() == Material.JUKEBOX
				&& recordDurations.containsKey(e.getPlayer().getItemInHand().getType())) {
			Jukebox box = (Jukebox) e.getClickedBlock().getState();
			LoopingJukebox jb = LoopingJukebox.getAt(this, box.getLocation());
			Material record = e.getPlayer().getItemInHand().getType();
			if (!box.isPlaying()) {
				e.setCancelled(true);
				box.setPlaying(record);
				e.getPlayer().setItemInHand(new ItemStack(Material.AIR));
				jb.onInsert();
			} else {
				jb.onEject();
			}
		}
	}
}
