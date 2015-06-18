package com.minecave.votecoins;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Lists;
import com.minecave.votecoins.chest.ChestGUI;
import com.minecave.votecoins.coin.CoinManager;
import com.minecave.votecoins.coin.VoteType;
import com.minecave.votecoins.util.FileUtils;
import com.minecave.votecoins.util.NameFetcher;
import com.minecave.votecoins.util.sql.MySQL;
import com.minecave.votecoins.util.sql.Table;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;

public class Main extends JavaPlugin implements Listener {
	public static Main plugin;
	
	private static CoinManager coins;
	private static ChestGUI shop;
	
	private Table votes, voteTracking;
	
	private List<Vote> toReward = Lists.newArrayList();
	
	private FileUtils files;
	private String prefix, coinMessage, purchaseMessage;
	private String[] rewardsMessage, voteMessage;
	
	private List<String> voteTopDay;
	private List<String> voteTopMonth;
	private List<String> voteTopAllTime;
	
	private List<String> day, month, allTime;
	
	private boolean debug;
	
	@Override
	public void onEnable() {
		plugin = this;
		
		files = new FileUtils(this);
		
		if (MySQL.getInstance() == null)
			new MySQL(this);
		
		makeStrings();
		
		coins = new CoinManager(this);
		shop = new ChestGUI(this);
		
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(shop, this);
		
		votes = Table.getTable("VOTES");
		voteTracking = Table.getTable("VOTE_TRACKING");
		
		voteTopDay = getConfig().getStringList("messages.votetop.day");
		voteTopMonth = getConfig().getStringList("messages.votetop.month");
		voteTopAllTime = getConfig().getStringList("messages.votetop.all time");
		
		day = Lists.newArrayList();
		month = Lists.newArrayList();
		allTime = Lists.newArrayList();
		
		debug = getConfig().getBoolean("debug");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equalsIgnoreCase("votecoin")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("You must go in-game to use this command.");
				return true;
			}
			
			final Player player = (Player) sender;
			
			if (args.length > 0 && player.isOp() && args[0].equalsIgnoreCase("debug")) {
				debug = !debug;
				player.sendMessage("Debug set to: " + ChatColor.GOLD + debug);
				return true;
			}
			
			if (args.length > 0 && player.isOp() && args[0].equalsIgnoreCase("give")) {
				if (args.length < 3) {
					player.sendMessage(colorize(prefix + " " + "&4Invalid syntax! Correct syntax: &2/vc give <player> <amount> [votes]"));
					return true;
				}
				
				Player target;
				int amount;
				try {
					target = Bukkit.getPlayer(args[1]);
				} catch (NullPointerException e) {
					player.sendMessage(colorize(prefix + " " + "&4Player not found! &2/vc give <player> <amount> [votes]"));
					return true;
				}
				try {
					amount = Integer.parseInt(args[2]);
				} catch (NumberFormatException e) {
					player.sendMessage(colorize(prefix + " " + "&4Invalid amount! &2/vc give <player> <amount> [votes]"));
					return true;
				}
				
				if (args.length > 3 && args[3].equalsIgnoreCase("votes")) {
					coins.add(target.getUniqueId().toString(), amount, VoteType.DAY, votes, voteTracking);
					player.sendMessage(colorize(prefix + " " + "&2You have given " + target.getName() + " " + amount + " votes."));
					return true;
				}
				
				coins.add(target.getUniqueId().toString(), amount, VoteType.COINS, votes, voteTracking);
				player.sendMessage(colorize(prefix + " " + "&2You have given " + target.getName() + " " + amount + " coins."));
				return true;
			}
			if (args.length > 0 && player.isOp() && args[0].equalsIgnoreCase("migrate")) {
				player.sendMessage("Starting migration from flat file to MySQL. This might take a little while..");
				new BukkitRunnable() {
					@Override
					public void run() {
						final File file = new File(plugin.getDataFolder() + File.separator + "votes.yml");
						if (file == null || !file.exists()) {
							player.sendMessage("Migration ended early because there's no votes.yml file. There's nothing to migrate!");
							cancel();
							return;
						}
						final FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
						
						double delay = 0.5;
						for (final String uuid : yaml.getConfigurationSection("coins").getKeys(false)) {
							delay += 0.5;
							new BukkitRunnable() {
								@Override
								public void run() {
									coins.add(uuid, yaml.getInt("coins." + uuid), VoteType.COINS, votes, voteTracking, false);
								}
							}.runTaskLater(plugin, (long) delay * 15);
						}
						
						delay = 0.5;
						for (final String uuid : yaml.getConfigurationSection("votes.all time").getKeys(false)) {
							delay += 0.5;
							new BukkitRunnable() {
								@Override
								public void run() {
									coins.add(uuid, yaml.getInt("votes.all time." + uuid), VoteType.ALL_TIME, votes, voteTracking, false);
								}
							}.runTaskLater(plugin, (long) delay * 15);
						}
						
						if (player != null && player.isOnline())
							player.sendMessage("All migration data is now being transferred. "
									+ "Time for you to sit back, relax, and wait for the final success message.");
						
						new BukkitRunnable() {
							@Override
							public void run() {
								if (player != null && player.isOnline())
									player.sendMessage("Migration is now 100% complete. "
											+ "The migrated data is now being removed from votes.yml "
											+ "so you don't have to worry about it anymore. "
											+ "Have a nice day!");
								yaml.set("coins", null);
								yaml.set("votes.all time", null);
								try {
									yaml.save(file);
								} catch (IOException e) {
									if (player != null && player.isOnline())
										player.sendMessage("Error saving votes.yml after completion. :(");
								}
								
							}
						}.runTaskLater(plugin, (long) delay * 15);
					}
				}.runTaskAsynchronously(this);
			}
			
			debug("pending tasks: " + getServer().getScheduler().getPendingTasks().size());
			debug("active threads: " + Thread.activeCount());
			coins.sendMessage(player, coinMessage, votes);
			return true;
		} else if (command.getName().equalsIgnoreCase("vcshop")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("You must go in-game to use this command.");
				return true;
			}
			
			Player player = (Player) sender;
			shop.openShop(player);
			return true;
		} else if (command.getName().equalsIgnoreCase("rewards")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be executed in-game.");
				return true;
			}
			
			Player player = (Player) sender;
			for (String s : rewardsMessage) {
				player.sendMessage(s);
			}
			return true;
		} else if (command.getName().equalsIgnoreCase("vote")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be executed in-game.");
				return true;
			}
			
			Player player = (Player) sender;
			for (String s : voteMessage) {
				player.sendMessage(s);
			}
			return true;
		} else if (command.getName().equalsIgnoreCase("votetop")) {
			if (args.length > 0) {
				switch (args[0].toLowerCase()) {
				case "day":
					sendTop(sender, VoteType.DAY);
					return true;
				case "forever":
				case "alltime":
				case "all-time":
					sendTop(sender, VoteType.ALL_TIME);
					return true;
				}
			}
			
			sendTop(sender, VoteType.MONTH);
			return true;
		}
		return true;
	}
	
	@EventHandler
	public void onVote(VotifierEvent event) {
		debug("A vote was received and is being handled.");
		addVote(event.getVote());
	}
	
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		final String name = player.getName();
		
		new BukkitRunnable() {
			@Override
			public void run() {
				List<Vote> toRemove = Lists.newArrayList();
				for (Vote vote : toReward) {
					if (vote.getUsername().equals(name)) {
						addVote(vote);
						toRemove.add(vote);
					}
				}
				
				for (Vote vote : toRemove)
					if (toReward.contains(vote))
						toReward.remove(vote);
			}
		}.runTaskLater(this, 5l);
	}
	
	private void addVote(final Vote vote) {
		final Player player = Bukkit.getPlayer(vote.getUsername());
		
		if (player == null || !player.isOnline()) {
			debug("Adding vote for player " + player.getName());
			toReward.add(vote);
		} else {
			debug("Storing vote to add when a player is online.");
			coins.add(player, vote, votes, voteTracking);
		}
	}
	
	@Override
	public FileConfiguration getConfig() {
		return files.getConfig();
	}
	
	@Override
	public void saveConfig() {
		files.saveConfig();
	}
	
	@Override
	public void reloadConfig() {
		files.reloadConfig();
	}
	
	public String getPrefix() {
		return prefix;
	}
	
	public String getPurchaseMessage() {
		return purchaseMessage;
	}
	
	public CoinManager getCoins() {
		return coins;
	}
	
	public Table getVotes() {
		return votes;
	}
	
	public Table getVoteTracking() {
		return voteTracking;
	}
	
	public boolean debug() {
		return debug;
	}
	
	public void debug(String message) {
		if (debug())
			getLogger().log(Level.WARNING, "[debug]" + " " + message);
	}
	
	private String colorize(String input) {
		return ChatColor.translateAlternateColorCodes('&', input);
	}
	
	private void makeStrings() {
		FileConfiguration config = files.getConfig();
		prefix = colorize(config.getString("prefix"));
		coinMessage = colorize(config.getString("coins message").replaceAll("<prefix>", prefix));
		purchaseMessage = colorize(config.getString("purchase successful message")).replaceAll("<prefix>", prefix).replaceAll("<coins message>", coinMessage.replaceAll(prefix, ""));
		
		if (config.isList("messages.rewards")) {
			List<String> list = config.getStringList("messages.rewards");
			rewardsMessage = new String[list.size()];
			for (int i = 0; i < list.size(); i++) { rewardsMessage[i] = colorize(list.get(i)); }
		} else { rewardsMessage = new String[] { "" }; }
		
		if (config.isList("messages.vote")) {
			List<String> list = config.getStringList("messages.vote");
			voteMessage = new String[list.size()];
			for (int i = 0; i < list.size(); i++) { voteMessage[i] = colorize(list.get(i)); }
		} else { voteMessage = new String[] { "" }; }
	}
	
	private void sendTop(CommandSender sender, VoteType type) {
		switch (type) {
		case DAY:
			for (String msg : day)
				sender.sendMessage(msg);
			break;
		case ALL_TIME:
			for (String msg : allTime)
				sender.sendMessage(msg);
			break;
		default:
			for (String msg : month)
				sender.sendMessage(msg);
			break;
		}
	}
	
	public void makeVoteTop() {
		debug("Making vote top...");
		new BukkitRunnable() {
			@Override
			public void run() {
				day = Lists.newArrayList();
				for (int i = 0; i < voteTopDay.size(); i++) {
					String s = voteTopDay.get(i);
					int repeat = s.contains("<x") ? Integer.valueOf(s.split("<x")[1].replace(">", "")) : 1;
					Iterator<Entry<String, Integer>> iterator = coins.getTop(VoteType.DAY).iterator();
					for (int i2 = 0; i2 < repeat; i2++) {
						if (!iterator.hasNext())
							return;
						Entry<String, Integer> next = iterator.next();
						try {
							UUID uuid = UUID.fromString(next.getKey());
							day.add(colorize(s.
									replaceAll("<#>", i2 + 1 + "").
									replaceAll("<name>", new NameFetcher(uuid).call().get(uuid)).
									replaceAll("<votes>", next.getValue() + "").
									replaceAll("<x" + repeat + ">", "")));
						} catch (Exception e) {
							e.printStackTrace();
							day.add(colorize(s.
									replaceAll("<#>", i2 + 1 + "").
									replaceAll("<name>", "unknown").
									replaceAll("<votes>", next.getValue() + "").
									replaceAll("<x" + repeat + ">", "")));
						}
					}
				}
			}
		}.runTaskAsynchronously(this);
		new BukkitRunnable() {
			@Override
			public void run() {
				month = Lists.newArrayList();
				for (int i = 0; i < voteTopMonth.size(); i++) {
					String s = voteTopMonth.get(i);
					int repeat = s.contains("<x") ? Integer.valueOf(s.split("<x")[1].replace(">", "")) : 1;
					Iterator<Entry<String, Integer>> iterator = coins.getTop(VoteType.MONTH).iterator();
					for (int i2 = 0; i2 < repeat; i2++) {
						if (!iterator.hasNext())
							return;
						Entry<String, Integer> next = iterator.next();
						try {
							UUID uuid = UUID.fromString(next.getKey());
							month.add(colorize(s.
									replaceAll("<#>", i2 + 1 + "").
									replaceAll("<name>", new NameFetcher(uuid).call().get(uuid)).
									replaceAll("<votes>", next.getValue() + "").
									replaceAll("<x" + repeat + ">", "")));
						} catch (Exception e) {
							e.printStackTrace();
							month.add(colorize(s.
									replaceAll("<#>", i2 + 1 + "").
									replaceAll("<name>", "unknown").
									replaceAll("<votes>", next.getValue() + "").
									replaceAll("<x" + repeat + ">", "")));
						}
					}
				}
			}
		}.runTaskAsynchronously(this);
		new BukkitRunnable() {
			@Override
			public void run() {
				allTime = Lists.newArrayList();
				for (int i = 0; i < voteTopAllTime.size(); i++) {
					String s = voteTopAllTime.get(i);
					int repeat = s.contains("<x") ? Integer.valueOf(s.split("<x")[1].replace(">", "")) : 1;
					Iterator<Entry<String, Integer>> iterator = coins.getTop(VoteType.ALL_TIME).iterator();
					for (int i2 = 0; i2 < repeat; i2++) {
						if (!iterator.hasNext())
							return;
						Entry<String, Integer> next = iterator.next();
						try {
							UUID uuid = UUID.fromString(next.getKey());
							allTime.add(colorize(s.
									replaceAll("<#>", i2 + 1 + "").
									replaceAll("<name>", new NameFetcher(uuid).call().get(uuid)).
									replaceAll("<votes>", next.getValue() + "").
									replaceAll("<x" + repeat + ">", "")));
						} catch (Exception e) {
							e.printStackTrace();
							allTime.add(colorize(s.
									replaceAll("<#>", i2 + 1 + "").
									replaceAll("<name>", "unknown").
									replaceAll("<votes>", next.getValue() + "").
									replaceAll("<x" + repeat + ">", "")));
						}
					}
				}
			}
		}.runTaskAsynchronously(this);
	}
}