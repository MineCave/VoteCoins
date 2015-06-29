package com.minecave.votecoins.util.sql;

import java.util.Map;


public class SQLNewPlayer extends SQLAction {

	final String uuid;
	final Table table;
	final Map<String, String> values;
	final boolean forceNew;
	
	public SQLNewPlayer(String uuid, Table table, Map<String, String> values, boolean forceNew) {
		super();
		this.uuid = uuid;
		this.table = table;
		this.values = values;
		this.forceNew = forceNew;
	}
	
	@Override
	public final void run() {
		MySQL.getInstance().makeNewPlayer(uuid, table, values, forceNew);
		_done();
	}

}