package com.robinkirkman.ircbridge;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.Listener;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.KickEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;

public class MessagePump {
	@FunctionalInterface
	private interface EndpointEventHandler<T extends Event> {
		public void handle(Endpoint origin, T event);
	}
	
	private static Map<Class<?>, EndpointEventHandler<?>> endpointEventHandlers = new ConcurrentHashMap<>();
	static {
		handle(DisconnectEvent.class, (o, e) -> {
			o.setReady(false);
		});
		handle(JoinEvent.class, (o, e) -> {
			String bnick = o.getBot().getUserBot().getNick();
			String enick = e.getUser().getNick();
			String echannel = e.getChannel().getName();
			if(bnick.equals(enick) && o.getChannel().equals(echannel))
				o.setReady(true);
		});
		handle(KickEvent.class, (o, e) -> {
			String bnick = o.getBot().getUserBot().getNick();
			String enick = e.getUser().getNick();
			String echannel = e.getChannel().getName();
			if(bnick.equals(enick) && o.getChannel().equals(echannel))
				o.setReady(false);
		});
		handle(PartEvent.class, (o, e) -> {
			String bnick = o.getBot().getUserBot().getNick();
			String enick = e.getUser().getNick();
			String echannel = e.getChannel().getName();
			if(bnick.equals(enick) && o.getChannel().equals(echannel))
				o.setReady(false);
		});
		handle(QuitEvent.class, (o, e) -> {
			String bnick = o.getBot().getUserBot().getNick();
			String enick = e.getUser().getNick();
			if(bnick.equals(enick))
				o.setReady(false);
		});
	}
	private static <T extends Event> void handle(Class<T> type, EndpointEventHandler<T> handler) {
		endpointEventHandlers.put(type, handler);
	}
	
	@FunctionalInterface
	private interface EndpointEventPublisher<T extends Event> {
		public void publish(Endpoint origin, Endpoint destination, T event);
	}
	
	private static Map<Class<?>, EndpointEventPublisher<?>> endpointEventPublishers = new ConcurrentHashMap<>();
	static {
		publish(MessageEvent.class, (o, d, e) -> {
			String prefix = String.format("[%s/%s] ", o.getName(), e.getUser().getNick());
			d.getBot().send().message(d.getChannel(), prefix + e.getMessage());
		});
		publish(ActionEvent.class, (o, d, e) -> {
			String prefix = String.format("* [%s/%s] ", o.getName(), e.getUser().getNick());
			d.getBot().send().message(d.getChannel(), prefix + e.getAction());
		});
		publish(JoinEvent.class, (o, d, e) -> {
			String notice = String.format("[%s/%s] has joined the channel", o.getName(), e.getUser().getNick());
			d.getBot().send().notice(d.getChannel(), notice);
		});
		publish(PartEvent.class, (o, d, e) -> {
			String notice = String.format("[%s/%s] has left the channel: %s", o.getName(), e.getUser().getNick(), e.getReason());
			d.getBot().send().notice(d.getChannel(), notice);
		});
		publish(KickEvent.class, (o, d, e) -> {
			String notice = String.format("[%s/%s] has been kicked by %s: %s", o.getName(), e.getRecipient().getNick(), e.getUser().getNick(), e.getReason());
			d.getBot().send().notice(d.getChannel(), notice);
		});
		publish(QuitEvent.class, (o, d, e) -> {
			String notice = String.format("[%s/%s] has quit irc: %s", o.getName(), e.getUser().getNick(), e.getReason());
			d.getBot().send().notice(d.getChannel(), notice);
		});
		publish(NickChangeEvent.class, (o, d, e) -> {
			String notice = String.format("[%s/%s] is now known as %s", o.getName(), e.getOldNick(), e.getNewNick());
			d.getBot().send().notice(d.getChannel(), notice);
		});
	}
	
	private static <T extends Event> void publish(Class<T> type, EndpointEventPublisher<T> handler) {
		endpointEventPublishers.put(type, handler);
	}
	
	private class Endpoint implements Listener {
		private String name;
		private PircBotX bot;
		private String channel;
		private boolean muted;
		private Executor dispatcher;
		private AtomicBoolean ready = new AtomicBoolean(false);
		
		public Endpoint(String name, PircBotX bot, String channel, boolean muted) {
			this.name = name;
			this.bot = bot;
			this.channel = channel;
			this.muted = muted;
			
			this.dispatcher = Executors.newSingleThreadExecutor((r) -> {
				Thread t = new Thread(r);
				t.setDaemon(true);
				t.setName(String.format("[dispatcher/%s]", this.name));
				return t;
			});
			
			bot.getConfiguration().getListenerManager().addListener(this);
		}
		
		public void waitUntilReady() {
			synchronized(ready) {
				while(!ready.get()) {
					Util.checkedRun(() -> ready.wait());
				}
			}
		}
		
		public void setReady(boolean ready) {
			synchronized(this.ready) {
				this.ready.set(ready);
				this.ready.notifyAll();
			}
		}
		
		public void publish(Endpoint origin, Event event) {
			@SuppressWarnings("unchecked")
			EndpointEventPublisher<Event> h = (EndpointEventPublisher<Event>) endpointEventPublishers.get(event.getClass());
			if(h != null)
				dispatcher.execute(() -> { waitUntilReady(); h.publish(origin, this, event); });
		}

		@Override
		public void onEvent(Event event) throws Exception {
			@SuppressWarnings("unchecked")
			EndpointEventHandler<Event> h = (EndpointEventHandler<Event>) endpointEventHandlers.get(event.getClass());
			if(h != null)
				h.handle(this, event);
			if(!muted && endpointEventPublishers.containsKey(event.getClass())) {
				User user = Util.invokeGetter(User.class, "getUser", event, null);
				Channel channel = Util.invokeGetter(Channel.class, "getChannel", event, null);
				if(user != null && user.getNick().equals(bot.getUserBot().getNick()))
					return;
				if(channel != null && !channel.getName().equals(this.channel))
					return;
				MessagePump.this.publish(this, event);
			}
		}
		
		public String getName() {
			return name;
		}
		
		public PircBotX getBot() {
			return bot;
		}
		
		public String getChannel() {
			return channel;
		}
	}
	
	private Collection<Endpoint> endpoints = new CopyOnWriteArrayList<>();
	private AtomicBoolean running = new AtomicBoolean(true);
	
	private boolean netsplitMode;
	
	public void add(String name, PircBotX bot, String channel, boolean muted) {
		Endpoint e = new Endpoint(name, bot, channel, muted);
		endpoints.add(e);
	}
	
	public void shutdown(String message) {
		if(running.compareAndSet(true, false)) {
			message = message.replaceAll("[\r\n].*", "");
			for(Endpoint e : endpoints) {
				e.getBot().stopBotReconnect();
				e.getBot().sendRaw().rawLineNow("QUIT :" + message);
			}
		}
	}
	
	private void publish(Endpoint origin, Event event) {
		if(!running.get())
			return;
		User originUser = Util.invokeGetter(User.class, "getUser", event, null);
		System.out.println(String.format("[%s/%s] %s", origin.getName(), event.getClass().getSimpleName(), Events.toString(event)));
		for(Endpoint e : endpoints) {
			if(e == origin)
				continue;
			if(netsplitMode && originUser != null) {
				if(originUser.getNick().equals(e.getBot().getNick()))
					shutdown("netsplit over, shutting down");
			}
			e.publish(origin, event);
		}
	}
	
	public boolean isNetsplitMode() {
		return netsplitMode;
	}
	
	public void setNetsplitMode(boolean netsplitMode) {
		this.netsplitMode = netsplitMode;
	}
}
