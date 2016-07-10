package com.robinkirkman.ircbridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.events.ActionEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.KickEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;

public abstract class Events {
	private Events() {}
	
	@FunctionalInterface
	private static interface ToString<T extends Event> {
		public String toString(T event);
	}
	
	private static final Map<Class<? extends Event>, ToString<?>> toStrings = new ConcurrentHashMap<>();
	static {
		toString(MessageEvent.class, (e) -> String.format("<%s> %s", e.getUser().getNick(), e.getMessage()));
		toString(ActionEvent.class, (e) -> String.format("* %s %s", e.getUser().getNick(), e.getAction()));
		toString(JoinEvent.class, (e) -> String.format("%s has joined the channel", e.getUser().getNick()));
		toString(PartEvent.class, (e) -> String.format("%s has left the channel: %s", e.getUser().getNick(), e.getReason()));
		toString(KickEvent.class, (e) -> String.format("%s has been kicked by %s: %s", e.getRecipient().getNick(), e.getUser(), e.getReason()));
		toString(QuitEvent.class, (e) -> String.format("%s has quit irc: %s", e.getUser().getNick(), e.getReason()));
		toString(NickChangeEvent.class, (e) -> String.format("%s is now known as %s", e.getOldNick(), e.getNewNick()));
	}
	
	private static <T extends Event> void toString(Class<T> type, ToString<T> eventToString) {
		toStrings.put(type, eventToString);
	}
	
	public static String toString(Event event) {
		@SuppressWarnings("unchecked")
		ToString<Event> ts = (ToString<Event>) toStrings.get(event.getClass());
		return (ts != null) ? ts.toString(event) : event.toString();
	}
}
