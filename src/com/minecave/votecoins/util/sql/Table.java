package com.minecave.votecoins.util.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Table {
	private String name;
	private String statNames = "";
	private String namesWithTypes = "", delimitedValues = "";
	private Map<String, String> stats;
	private static List<Table> tables = Lists.newArrayList();
	
	// UUID has to be the first stat in every list.
	public static final Table VOTES;
	public static final Table VOTE_TRACKING;
	
	static {
		Map<String, String> map = Maps.newHashMap();
		map.put("uuid varchar(200)", "<uuid>");
		map.put("votecoins int(20)", "<votecoins>");
		map.put("votes int(20)", "<votes>");
		VOTES = new Table("VOTES", map);
	}
	
	static {
		Map<String, String> map = Maps.newHashMap();
		map.put("uuid varchar(200)", "<uuid>");
		map.put("date date", "<current date>");
		map.put("service varchar(200)", "<service>");
		VOTE_TRACKING = new Table("VOTE_TRACKING", map);
	}
	
	public Table(String name, Map<String, String> stats) {
		this.name = name;
		this.stats = stats;
		
		List<String> keys = new ArrayList<String>(stats.keySet());
		List<String> values = new ArrayList<String>(stats.values());
		Collections.reverse(keys);
		Collections.reverse(values);
		
		for (String nameAndType : keys) {
			if (!namesWithTypes.equals(""))
				namesWithTypes += ", ";
			namesWithTypes += nameAndType;
			
			if (!statNames.equals(""))
				statNames += ", ";
			statNames += nameAndType.split(" ")[0];
		}
		for (String value : values) {
			if (!delimitedValues.equals(""))
				delimitedValues += ", ";
			delimitedValues += value;
		}
		
		tables.add(this);
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public String name() {
		return name;
	}
	
	public Map<String, String> getStats() {
		return stats;
	}
	
	public String getStatNames() {
		return statNames;
	}
	
	public String getNamesWithTypes() {
		return namesWithTypes;
	}
	
	public String getDelimitedValues() {
		return delimitedValues;
	}
	
	public static Table getTable(String name) {
		for (Table table : tables) {
			if (table.name().equals(name))
				return table;
		}
		return null;
	}
}