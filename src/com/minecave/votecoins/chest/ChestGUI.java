package com.minecave.votecoins.chest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.minecave.votecoins.Main;

public class ChestGUI implements Listener {
	private List<UUID> inShop = Lists.newArrayList();
	private List<ChestSlot> slots = Lists.newArrayList();
	private Map<UUID, ItemStack[]> inventories = Maps.newHashMap();
	private Main plugin;
	private Inventory chest;
	
	public ChestGUI(Main plugin) {
		this.plugin = plugin;
		ChestSlot.setMain(plugin);
		initShop();
	}
	
	/**
	 * Opens up the shop inventory for a player.
	 * @param player the player who should open the shop inventory
	 */
	public void openShop(Player player) {
		player.openInventory(chest);
		inShop.add(player.getUniqueId());
	}
	
	private void initShop() {
		ConfigurationSection shopSection = plugin.getConfig().getConfigurationSection("shop");
		chest = Bukkit.createInventory(null, shopSection.getInt("size"), ChatColor.translateAlternateColorCodes('&', shopSection.getString("title")));
		
		for (String slot : shopSection.getConfigurationSection("slots").getKeys(false)) {
			int slotNumber = Integer.parseInt(slot) - 1;
			ConfigurationSection slotSection = shopSection.getConfigurationSection("slots." + slot);
			
			chest.setItem(slotNumber, buildChestSlot(slotSection, slotNumber).getItem());
		}
	}
	
	private ChestSlot buildChestSlot(ConfigurationSection slotSection, int slotNumber) {
		ChestSlot.Builder builder = new ChestSlot.Builder(slotNumber);
		
		builder.permissions(slotSection.getString("permissions"));
		builder.cost(slotSection.getInt("cost"));
		builder.item(slotSection.getConfigurationSection("item"));
		builder.commands(slotSection.getString("commands"));
		
		ChestSlot slot = builder.build();
		slots.add(slot);
		return slot;
	}
	
	private ChestSlot getChestSlot(int slot) {
		for (ChestSlot chestSlot : slots) {
			if (chestSlot.getSlot() == slot)
				return chestSlot;
		}
		return null;
	}
	
	private void removeFromShop(UUID uuid) {
		if (inShop.contains(uuid)) {
			inShop.remove(uuid);
			if (inventories.containsKey(uuid)) {
				ItemStack[] contents = inventories.get(uuid);
				Bukkit.getPlayer(uuid).getInventory().setContents(contents);
				inventories.remove(uuid);
			}
		}
	}
	
	/* ========== Events from here on out. ========== */
	
	@EventHandler (priority = EventPriority.HIGHEST)
	private void onBuy(InventoryClickEvent event) {
		if (!inShop.contains(event.getWhoClicked().getUniqueId()))
			return;
		
		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player))
			return;
		
		final Player player = (Player) event.getWhoClicked();
		inventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
		new BukkitRunnable() {
			@Override
			public void run() {
				player.closeInventory();
			}
		}.runTaskLater(plugin, 1l);
		
		if (!event.getClickedInventory().equals(event.getView().getTopInventory()))
			return;
		
		ChestSlot slot = getChestSlot(event.getSlot());
		if (slot != null)
			slot.buy(player, plugin.getVotes(), plugin.getVoteTracking());
	}
	
	@EventHandler (priority = EventPriority.HIGHEST)
	private void onInventoryClose(InventoryCloseEvent event) {
		removeFromShop(event.getPlayer().getUniqueId());
	}
	
	@EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
	private void onLeave(PlayerQuitEvent event) {
		removeFromShop(event.getPlayer().getUniqueId());
	}
	
	@EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
	private void onLeave(PlayerKickEvent event) {
		removeFromShop(event.getPlayer().getUniqueId());
	}
}