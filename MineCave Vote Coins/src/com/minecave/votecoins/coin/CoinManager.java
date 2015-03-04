package com.minecave.votecoins.coin;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;

import net.minecraft.util.com.google.common.collect.Maps;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.minecave.votecoins.Main;
import com.minecave.votecoins.util.sql.MySQL;
import com.minecave.votecoins.util.sql.SQLGet;
import com.minecave.votecoins.util.sql.SQLNewPlayer;
import com.minecave.votecoins.util.sql.SQLSet;
import com.minecave.votecoins.util.sql.Table;
import com.vexsoftware.votifier.model.Vote;

public class CoinManager {
	private Main plugin;
	private SortedSet<Map.Entry<String, Integer>> sortedCoins, sortedDay, sortedMonth, sortedAllTime;
	
	public CoinManager(Main plugin) {
		this.plugin = plugin;
		
		sortedCoins = new TreeSet<Map.Entry<String, Integer>>();
		sortedDay = new TreeSet<Map.Entry<String, Integer>>();
		sortedMonth = new TreeSet<Map.Entry<String, Integer>>();
		sortedAllTime = new TreeSet<Map.Entry<String, Integer>>();
		
		startOrdering(plugin.getConfig().getInt("sort votetop interval") * 20);
	}
	
	public void sendMessage(final Player player, final String message, final Table votes) {
		new SQLNewPlayer(player.getUniqueId().toString(), votes) {
			@Override
			protected void done() {
				new SQLGet(player.getUniqueId().toString(), "votecoins", votes) {
					@Override
					protected void done() {
						if (player != null && player.isOnline())
							player.sendMessage(message.replaceAll("<coins>", result == null ? "0" : result + ""));
					}
				};
			}
		};
	}
	
	public void add(Player player, final Vote vote, final Table votes, final Table voteTracking) {
		final String uuid = player.getUniqueId().toString();
		
		new SQLNewPlayer(uuid, votes) {
			@Override
			protected void done() {
				new SQLGet(uuid, "votecoins", votes) {
					@Override
					protected void done() {
						int oldAmount = result == null ? 0 : (int) result;
						new SQLSet(uuid, "votecoins", oldAmount + 1, votes);
					}
				};
				new SQLGet(uuid, "votes", votes) {
					@Override
					protected void done() {
						int oldAmount = result == null ? 0 : (int) result;
						checkReward(uuid, oldAmount, 1);
						new SQLSet(uuid, "votes", oldAmount + 1, votes);
					}
				};
			}
		};
		new SQLNewPlayer(uuid, voteTracking) {
			@Override
			protected void done() {
				new SQLSet(uuid, "date", new Date(System.currentTimeMillis()), voteTracking);
				new SQLSet(uuid, "service", vote.getServiceName(), voteTracking);
			}
		};
	}
	
	public void add(final String uuid, final int amount, VoteType type, final Table votes, final Table voteTracking) {
		add(uuid, amount, type, votes, voteTracking, true);
	}
	
	public void add(final String uuid, final int amount, VoteType type, final Table votes, final Table voteTracking, final boolean reward) {
		switch (type) {
		case COINS:
			new SQLNewPlayer(uuid, votes) {
				@Override
				protected void done() {
					new SQLGet(uuid, "votecoins", votes) {
						@Override
						protected void done() {
							int oldAmount = result == null ? 0 : (int) result;
							Bukkit.broadcastMessage("oldAmount: " + oldAmount);
							new SQLSet(uuid, "votecoins", oldAmount + amount, votes);
						}
					};
				}
			};
			for (int i = 0; i < amount; i++) {
				new SQLNewPlayer(uuid, voteTracking) {
					@Override
					protected void done() {
						new SQLSet(uuid, "date", new Date(System.currentTimeMillis()), voteTracking);
						new SQLSet(uuid, "service", "unknown", voteTracking);
					}
				};
			}
			break;
		default:
			new SQLNewPlayer(uuid, votes) {
				@Override
				protected void done() {
					new SQLGet(uuid, "votes", votes) {
						@Override
						protected void done() {
							int oldAmount = result == null ? 0 : (int) result;
							if (reward)
								checkReward(uuid, oldAmount, amount);
							new SQLSet(uuid, "votes", oldAmount + amount, votes);
						}
					};
				}
			};
			for (int i = 0; i < amount; i++) {
				new SQLNewPlayer(uuid, voteTracking) {
					@Override
					protected void done() {
						new SQLSet(uuid, "date", new Date(System.currentTimeMillis()), voteTracking);
						new SQLSet(uuid, "service", "unknown", voteTracking);
					}
				};
			}
			break;
		}
	}
	
	public SortedSet<Map.Entry<String, Integer>> getTop(VoteType type) {
		switch (type) {
		case COINS:
			return sortedCoins;
		case DAY:
			return sortedDay;
		case MONTH:
			return sortedMonth;
		case ALL_TIME:
			return sortedAllTime;
		default:
			return sortedAllTime;
		}
	}
	
	private void startOrdering(long interval) {
		new BukkitRunnable() {
			@Override
			public void run() {
				sortedCoins = sort(getTop(VoteType.COINS, null));
				sortedDay = sort(getTop(VoteType.DAY, null));
				sortedMonth = sort(getTop(VoteType.MONTH, null));
				sortedAllTime = sort(getTop(VoteType.ALL_TIME, null));
				plugin.makeVoteTop();
			}
		}.runTaskTimerAsynchronously(plugin, 20l, interval);
	}
	
	@SuppressWarnings("deprecation")
	private Map<String, Integer> getTop(VoteType type, Object toBeNull) {
		ResultSet set;
		Date today = new Date(System.currentTimeMillis());
		int size;
		
		try {
		switch (type) {
			case COINS:
				set = MySQL.getInstance().getAll(plugin.getVotes());
				Map<String, Integer> coins = Maps.newHashMap();
				size = 0;
				while (set.next() && size < 49) {
					size++;
					coins.put(set.getString("uuid"), set.getInt("votecoins"));
				}
				return coins;
			case DAY:
				set = MySQL.getInstance().getAll(plugin.getVoteTracking());
				Map<String, Integer> day = Maps.newHashMap();
				size = 0;
				while (set.next() && size < 49) {
					size++;
					Date date = set.getDate("date");
					if (date.getDay() != today.getDay() || date.getMonth() != today.getMonth())
						continue;
					String uuid = set.getString("uuid");
					if (day.containsKey(uuid)) {
						int amount = day.get(uuid);
						day.remove(uuid);
						day.put(uuid, amount + 1);
					} else {
						day.put(uuid, 1);
					}
				}
				return day;
			case MONTH:
				set = MySQL.getInstance().getAll(plugin.getVoteTracking());
				Map<String, Integer> month = Maps.newHashMap();
				size = 0;
				while (set.next() && size < 49) {
					size++;
					Date date = set.getDate("date");
					if (date.getMonth() != today.getMonth())
						continue;
					String uuid = set.getString("uuid");
					if (month.containsKey(uuid)) {
						int amount = month.get(uuid);
						month.remove(uuid);
						month.put(uuid, amount + 1);
					} else {
						month.put(uuid, 1);
					}
				}
				return month;
			case ALL_TIME:
				set = MySQL.getInstance().getAll(plugin.getVotes());
				Map<String, Integer> allTime = Maps.newHashMap();
				size = 0;
				while (set.next() && size < 49) {
					size++;
					allTime.put(set.getString("uuid"), set.getInt("votes"));
				}
				return allTime;
			default:
				return getTop(VoteType.ALL_TIME, null);
			}
		} catch (SQLException e) {
			Bukkit.getLogger().log(Level.WARNING, "MySQL error while trying to use votetop: " + e.getMessage());
			return Maps.newHashMap();
		}
	}
	
	/*private Main plugin;
	private FileConfiguration votes;
	private ConfigurationSection coinSection, voteSectionDay, voteSectionMonth, voteSectionAllTime, tracking;
	private Map<String, Integer> coins, day, month, allTime;
	private Calendar calendar = Calendar.getInstance();
	
	public CoinManager(Main plugin) {
		this.plugin = plugin;
		votes = plugin.getVotes();
		
		if (!votes.contains("coins"))
			votes.createSection("coins");
		if (!votes.contains("votes.tracking"))
			votes.createSection("votes.tracking");
		if (!votes.contains("votes.all time"))
			votes.createSection("votes.all time");
		if (!votes.contains("votes.month"))
			votes.createSection("votes.month");
		if (!votes.contains("votes.day"))
			votes.createSection("votes.day");
		
		plugin.saveVotes();
		
		coinSection = votes.getConfigurationSection("coins");
		tracking = votes.getConfigurationSection("votes.tracking");
		voteSectionDay = votes.getConfigurationSection("votes.day");
		voteSectionMonth = votes.getConfigurationSection("votes.month");
		voteSectionAllTime = votes.getConfigurationSection("votes.all time");
		
		initTracking();
	}
	*/
	/**
	 * Gets the amount of vote coins a player has.
	 * @param player the Player object of the player
	 * @param type the type of vote, for example COINS, MONTHLY, ALL_TIME, etc...
	 * @return the amount of vote coins the player has
	 *//*
	public int getVotes(Player player, VoteType type) {
		return getVotes(player.getUniqueId().toString(), type);
	}
	*/
	/**
	 * Gets the amount of vote coins a player has.
	 * @param uuid the unique identifier of the player (as a String)
	 * @param type the type of vote, for example COINS, MONTHLY, ALL_TIME, etc...
	 * @return the amount of vote coins the player has
	 *//*
	public int getVotes(String uuid, VoteType type) {
		switch (type) {
		case COINS:
			return (coinSection.contains(uuid) ? coinSection.getInt(uuid) : 0);
		case ALL_TIME:
			return (voteSectionAllTime.contains(uuid) ? voteSectionAllTime.getInt(uuid) : 0);
		case MONTH:
			return (voteSectionMonth.contains(uuid) ? voteSectionMonth.getInt(uuid) : 0);
		case DAY:
			return (voteSectionDay.contains(uuid) ? voteSectionDay.getInt(uuid) : 0);
		default:
			return -1;
		}
	}*/
	
	/**
	 * Adds votes (or coins) to a player.
	 * @param player the Player object of the player
	 * @param amount the amount of coins or votes will be added to the player's balance
	 * @param type the type of vote, for example COINS, MONTHLY, ALL_TIME, etc...
	 *//*
	public void addVotes(Player player, int amount, VoteType type) {
		addVotes(player.getUniqueId().toString(), amount, type);
	}
	*/
	/**
	 * Adds votes (or coins) to a player.
	 * @param player the Player object of the player
	 * @param amount the amount of coins or votes will be added to the player's balance
	 * @param type the type of vote, for example COINS, MONTHLY, ALL_TIME, etc...
	 *//*
	public void addVotes(String uuid, int amount, VoteType type) {
		switch (type) {
		case COINS:
			coinSection.set(uuid, getVotes(uuid, type) + amount);
			break;
		case ALL_TIME:
			checkReward(uuid, getVotes(uuid, type), amount);
			voteSectionAllTime.set(uuid, getVotes(uuid, type) + amount);
			voteSectionMonth.set(uuid, getVotes(uuid, VoteType.MONTH) + amount);
			voteSectionDay.set(uuid, getVotes(uuid, VoteType.DAY) + amount);
			break;
		case MONTH:
			checkReward(uuid, getVotes(uuid, type), amount);
			voteSectionMonth.set(uuid, getVotes(uuid, type) + amount);
			voteSectionAllTime.set(uuid, getVotes(uuid, VoteType.ALL_TIME) + amount);
			voteSectionDay.set(uuid, getVotes(uuid, VoteType.DAY) + amount);
			break;
		case DAY:
			checkReward(uuid, getVotes(uuid, type), amount);
			voteSectionDay.set(uuid, getVotes(uuid, type) + amount);
			voteSectionMonth.set(uuid, getVotes(uuid, VoteType.MONTH) + amount);
			voteSectionAllTime.set(uuid, getVotes(uuid, VoteType.ALL_TIME) + amount);
			break;
		}
		plugin.saveVotes();
	}*/
	
	/**
	 * Tries to update the votes based on the time.
	 * For example, votes from yesterday will be added to today if it's the last day saved in the config is before this day.
	 *//*
	public void checkUpdate() {
		String[] lastUpdate = tracking.getString("last update").split(",");
		String[] currentTime = serializeDate().split(",");
		int lastUpdateDay = Integer.parseInt(lastUpdate[0]);
		int lastUpdateMonth = Integer.parseInt(lastUpdate[1]);
		int currentTimeDay = Integer.parseInt(currentTime[0]);
		int currentTimeMonth = Integer.parseInt(currentTime[1]);
		
		if (lastUpdateDay != currentTimeDay) 						// At least one day has passed since the votes were last updated.
			votes.set("votes.tracking.day", "");						// Reset today's votes.
		
		if (lastUpdateMonth != currentTimeMonth)						// At least one month has passed since the votes were last updated.
			votes.set("votes.tracking.month", "");					// Reset this month's votes.
		
		tracking.set("last update", serializeDate());				// Update the last update section in the tracking file.
		plugin.saveVotes();
	}
	
	public SortedSet<Map.Entry<String, Integer>> getTop(VoteType type) {
		switch (type) {
		case COINS:
			if (coins == null)
				coins = Maps.newHashMap();
			addAll(coins, coinSection);
			return sort(coins);
		case DAY:
			if (day == null)
				day = Maps.newHashMap();
			addAll(day, voteSectionDay);
			return sort(day);
		case MONTH:
			if (month == null)
				month = Maps.newHashMap();
			addAll(month, voteSectionMonth);
			return sort(month);
		case ALL_TIME:
			if (allTime == null)
				allTime = Maps.newHashMap();
			addAll(allTime, voteSectionAllTime);
			return sort(allTime);
		default:
			if (month == null)
				month = Maps.newHashMap();
			addAll(month, voteSectionMonth);
			return sort(month);
		}
	}
	
	private void addAll(Map<String, Integer> map, ConfigurationSection section) {
		for (String uuid : section.getKeys(false)) {
			map.put(uuid, section.getInt(uuid));
		}
	}
	
	private void initTracking() {
		if (!tracking.contains("start"))
			tracking.set("start", serializeDate());
		if (!tracking.contains("last update"))
			tracking.set("last update", serializeDate());
		new BukkitRunnable() {
			@Override
			public void run() {
				checkUpdate();
			}
		}.runTaskTimer(plugin, 1l, 20l * 60 * 60);
		plugin.saveVotes();
	}
	
	private String serializeDate() {
		return calendar.get(Calendar.DAY_OF_MONTH) + "," + calendar.get(Calendar.MONTH) + "," + calendar.get(Calendar.YEAR);
	}
	*/
	private <K,V extends Comparable<? super V>> SortedSet<Map.Entry<K,V>> sort(Map<K,V> map) {
		SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<Map.Entry<K, V>>(
				new Comparator<Map.Entry<K, V>>() {
					@Override
					public int compare(Map.Entry<K, V> e1, Map.Entry<K, V> e2) {
						int res = e2.getValue().compareTo(e1.getValue());
						return res != 0 ? res : 1;
					}
				});
		sortedEntries.addAll(map.entrySet());
		return sortedEntries;
	}
	
	private void checkReward(String uuid, int original, int addition) {
		int total = original + addition;
		for (String s : plugin.getConfig().getConfigurationSection("rewards").getKeys(false)) {
			int value = Integer.parseInt(s);
			if (original < value && total >= value) {
				ConfigurationSection reward = plugin.getConfig().getConfigurationSection("rewards." + s);
				Player player = Bukkit.getPlayer(UUID.fromString(uuid));
				executeCommands(player, reward.getString("commands").contains(",") ? reward.getString("commands").split(",") : new String[] { reward.getString("commands") });
				player.sendMessage(colorize(reward.getString("message").replaceAll("<player>", player.getName())));
			}
		}
	}
	
	private void executeCommands(Player player, String[] commands) {
		for (String command : commands) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), colorize(command.replaceAll("<player>", player.getName())));
		}
	}
	
	private static String colorize(String input) {
		return ChatColor.translateAlternateColorCodes('&', input);
	}
}