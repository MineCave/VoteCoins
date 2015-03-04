package com.minecave.votecoins.chest;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.minecave.votecoins.Main;
import com.minecave.votecoins.coin.VoteType;
import com.minecave.votecoins.util.sql.SQLGet;
import com.minecave.votecoins.util.sql.SQLNewPlayer;
import com.minecave.votecoins.util.sql.Table;

public class ChestSlot {
	private static Main main;
	private int slot;
	private String[] commands;
	private String[] permissions;
	private ItemStack item;
	private int cost;
	
	public int getSlot() {
		return slot;
	}
	
	public ItemStack getItem() {
		return item;
	}
	
	/**
	 * Attempts to make a player buy a slot.
	 * @param player the buyer
	 * @param votes the votes table
	 * @param voteTracking the vote tracking table
	 */
	public void buy(final Player player, final Table votes, final Table voteTracking) {
		for (String permission : permissions) {
			if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
				player.sendMessage(colorize(main.getPrefix() + " " + "&4You cannot buy this."));
				return;
			}
		}
		
		new SQLNewPlayer(player.getUniqueId().toString(), votes) {
			@Override
			protected void done() {
				new SQLGet(player.getUniqueId().toString(), "votecoins", votes) {
					@Override
					protected void done() {
						int voteCoins = (int) result;
						if (voteCoins < cost) {
							player.sendMessage(colorize(main.getPrefix() + " " + "&4You do not have enough money to purchase this."));
							return;
						}

						main.getCoins().add(player.getUniqueId().toString(), 0 - cost, VoteType.COINS, votes, voteTracking);
						main.getCoins().sendMessage(player, main.getPurchaseMessage(), votes);
						main.getCoins().sendMessage(player, main.getPurchaseMessage(), votes);
						new BukkitRunnable() {
							@Override
							public void run() {
								executeCommands(player);
							}
						}.runTaskLater(main, 5l);
					}
				};
			}
		};
	}
	
	public void executeCommands(Player player) {
		for (String command : commands) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceAll(command, player.getName()));
		}
	}
	
	private List<String> replaceAll(List<String> input) {
		for (String s : input) {
			replaceAll(s);
		}
		return input;
	}
	
	private String replaceAll(String input, String playerName) {
		input = input.replaceAll("<prefix>", main.getPrefix()).replaceAll("<cost>", cost + "").replaceAll("<player>", playerName);
		return input;
	}
	
	private String replaceAll(String input) {
		input = input.replaceAll("<prefix>", main.getPrefix()).replaceAll("<cost>", cost + "");
		return input;
	}
	
	private static String colorize(String input) {
		return ChatColor.translateAlternateColorCodes('&', input);
	}
	
	public static void setMain(Main main) {
		ChestSlot.main = main;
	}
	
	private ChestSlot(Builder builder) {
		this.slot = builder.slot;
		this.commands = builder.commands;
		this.permissions = builder.permissions;
		this.item = builder.item;
		this.cost = builder.cost;
		
		ItemMeta meta = item.getItemMeta();
		if (meta.hasDisplayName())
			meta.setDisplayName(replaceAll(meta.getDisplayName()));
		if (meta.hasLore())
			meta.setLore(replaceAll(meta.getLore()));
		item.setItemMeta(meta);
	}
	
	public static class Builder {
		private int slot;
		private String[] commands;
		private String[] permissions;
		private ItemStack item;
		private int cost;
		
		public Builder(int slot) {
			this.slot = slot;
		}
		
		public Builder commands(String commands) {
			this.commands = parseCommands(commands);
			return this;
		}
		
		public Builder item(ConfigurationSection itemSection) {
			this.item = parseItem(itemSection);
			return this;
		}
		
		public Builder cost(int cost) {
			this.cost = cost;
			return this;
		}
		
		public Builder permissions(String permission) {
			this.permissions = permission == null || permission.isEmpty() || permission.equalsIgnoreCase("none") ? new String[0] : parsePermissions(permission);
			return this;
		}
		
		public ChestSlot build() {
			return new ChestSlot(this);
		}
		
		private ItemStack parseItem(ConfigurationSection itemSection) {
			String itemInput = itemSection.getString("item");
			ItemStack item = null;
			if (itemInput.contains(":")) {
				String[] itemInfo = itemInput.split(":");
				item = new ItemStack(getMaterial(itemInfo[0]), Integer.valueOf(itemInfo[1]));
			} else {
				item = new ItemStack(getMaterial(itemInput));
			}
			
			ItemMeta meta = item.getItemMeta();
			if (itemSection.contains("name"))
				meta.setDisplayName(ChestSlot.colorize(itemSection.getString("name")));
			if (itemSection.contains("lore"))
				meta.setLore(colorize(itemSection.getStringList("lore")));
			item.setItemMeta(meta);
			return item;
		}
		
		private String[] parseCommands(String commandInput) {
			String[] commandInfo;
			if (commandInput.contains(";"))
				commandInfo = commandInput.split(";");
			else
				commandInfo = new String[] { commandInput };
			return commandInfo;
		}
		
		private String[] parsePermissions(String permissionInput) {
			String[] permissions;
			if (permissionInput.contains(";"))
				permissions = permissionInput.split(";");
			else
				permissions = new String[] { permissionInput };
			return permissions;
		}
		
		private Material getMaterial(String inputMaterial) {
			return Material.valueOf(inputMaterial.toUpperCase().replaceAll(" ", "_"));
		}
		
		private List<String> colorize(List<String> strings) {
			for (String string : strings) {
				ChestSlot.colorize(string);
			}
			return strings;
		}
	}
}