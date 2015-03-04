package com.minecave.votecoins.util.sql;

import org.bukkit.scheduler.BukkitRunnable;

import com.minecave.votecoins.Main;

public abstract class SQLAction implements Runnable {
	
	boolean done = false;
	
	public SQLAction() {
		final SQLAction action = this;
		new BukkitRunnable() {
			public void run() {
				action.run();
			}
		}.runTaskAsynchronously(Main.plugin);
	}
	
	public final boolean isDone() {
		return done;
	}
	
	final void _done() {
		done = true;
		new BukkitRunnable() {
			public void run() {
				done();
			}
		}.runTask(Main.plugin);
	}
	
	protected void done() {
		
	}

}