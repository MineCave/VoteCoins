package com.minecave.votecoins.util;
 
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import net.minecraft.util.com.google.common.collect.Maps;

import org.bukkit.Bukkit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
 
public class NameFetcher implements Callable<Map<UUID, String>> {
    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static Map<UUID, String> cache = Maps.newHashMap();
    private final JSONParser jsonParser = new JSONParser();
    private final List<UUID> uuids;
    
    public NameFetcher(List<UUID> uuids) {
        this.uuids = ImmutableList.copyOf(uuids);
    }
    
    public NameFetcher(UUID uuid) {
    	uuids = Lists.newArrayList(uuid);
    }
 
    @Override
    public Map<UUID, String> call() throws Exception {
        Map<UUID, String> uuidStringMap = new HashMap<UUID, String>();
        
        for (UUID uuid: uuids) {
        	if (cache.containsKey(uuid)) {
        		uuidStringMap.put(uuid, cache.get(uuid));
        		continue;

        	}
        	
        	try {
        		String name = Bukkit.getOfflinePlayer(uuid).getName().toString();
        		uuidStringMap.put(uuid, name);
        		continue;
        	} catch (NullPointerException e) {}
            HttpURLConnection connection = (HttpURLConnection) new URL(PROFILE_URL + uuid.toString().replace("-", "")).openConnection();
            JSONObject response = (JSONObject) jsonParser.parse(new InputStreamReader(connection.getInputStream()));
            String name = (String) response.get("name");
            if (name == null)
                continue;
            
            String cause = (String) response.get("cause");
            String errorMessage = (String) response.get("errorMessage");
            if (cause != null && cause.length() > 0) {
                throw new IllegalStateException(errorMessage);
            }
            uuidStringMap.put(uuid, name);
            cache.put(uuid, name);
        }
        
        return uuidStringMap;
    }
}