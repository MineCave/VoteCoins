package com.minecave.votecoins.util.sql;


public class SQLNewPlayer extends SQLAction {

	final String uuid;
	final Table table;
	
	public SQLNewPlayer(String uuid, Table table) {
		super();
		this.uuid = uuid;
		this.table = table;
	}
	
	@Override
	public final void run() {
		MySQL.getInstance().makeNewPlayer(uuid, table);
		_done();
	}

}