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

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Maps;
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
		
		startOrdering(plugin.getConfig().getInt("sort votetop interval") * 20 * 60);
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
		long start = System.currentTimeMillis();
		
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
		
		long end = System.currentTimeMillis();
		
		Main.plugin.debug("time to check reward: " + (end - start));
	}
	
	private void executeCommands(Player player, String[] commands) {
		for (String command : commands) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), colorize(command.replaceAll("<player>", player.getName())));
		}
	}
	
	private String colorize(String input) {
		return ChatColor.translateAlternateColorCodes('&', input);
	}
}