package com.minecave.votecoins.util.sql;

import org.bukkit.entity.Player;

public class SQLGet extends SQLAction {

	final String uuid;
	final String query;
	final Table table;
	protected Object result;
	
	public SQLGet(Player player, String query, Table table) {
		this(player.getUniqueId().toString(), query, table);
	}
	
	public SQLGet(String uuid, String query, Table table) {
		super();
		this.uuid = uuid;
		this.query = query;
		this.table = table;
	}
	
	@Override
	public final void run() {
		result = MySQL.getInstance().get(uuid, query, table);
		_done();
	}
	
}