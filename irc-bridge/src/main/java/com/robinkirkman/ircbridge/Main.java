package com.robinkirkman.ircbridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;

public class Main {
	private static final Options OPT = new Options();
	static {
		OPT.addOption("e", "endpoint", true, "<name>:<nick>:<channel>:<server>[:<port>[:<password>]]");
		OPT.addOption("m", "mute", true, "<name>");
		OPT.addOption("n", "netsplit", false, "netsplit mode");
	}
	
	public static void main(String[] args) throws Exception {
		CommandLine cli = new DefaultParser().parse(OPT, args);
		
		String[] v;
		if((v = cli.getOptionValues("endpoint")) == null || v.length < 2)
			throw new IllegalArgumentException("must supply at least 2 --endpoint=");
		
		String[] m = cli.getOptionValues("mute");
		Set<String> muted = (m == null) ? Collections.emptySet() : new HashSet<>(Arrays.asList(m));
		
		MessagePump pump = new MessagePump();
		pump.setNetsplitMode(cli.hasOption("netsplit"));
		
		Collection<Thread> botThreads = new ArrayList<>();
		
		for(String earg : cli.getOptionValues("endpoint")) {
			String[] f = earg.split(":", 6);
			String name = f[0];
			String nick = f[1];
			String channel = f[2];
			String host = f[3];
			int port = Integer.parseInt(f.length >= 5 ? f[4] : "6667");
			String password = (f.length == 6) ? f[5] : null;
			
			PircBotX bot = new PircBotX(createConfiguration(nick, channel, host, port, password));
			
			Thread t = new Thread(() -> Util.quietlyRun(() -> bot.startBot()));
			t.setDaemon(false);
			t.setName(String.format("[pircbotx/%s]", earg));
			botThreads.add(t);
			
			pump.add(name, bot, channel, muted.contains(name));
		}
		
		for(Thread t : botThreads)
			t.start();
	}
	
	private static Configuration createConfiguration(String nick, String channel, String host, int port, String password) {
		Configuration.Builder builder = new Configuration.Builder();
		builder.addServer(new Configuration.ServerEntry(host, port));
		if(password != null)
			builder.setServerPassword(password);
		builder.setName(nick);
		builder.setLogin(nick);
		builder.addAutoJoinChannel(channel);
		return builder.buildConfiguration();
	}
	
}
