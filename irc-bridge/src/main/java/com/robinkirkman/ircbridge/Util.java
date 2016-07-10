package com.robinkirkman.ircbridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Util {
	private Util() {}
	
	@FunctionalInterface
	public static interface ThrowingRunnable {
		public void run() throws Throwable;
	}
	
	@FunctionalInterface
	public static interface ThrowingCallable<T> {
		public T call() throws Throwable;
	}
	
	public static void checkedRun(ThrowingRunnable task) {
		try {
			task.run();
		} catch(Throwable t) {
			if(t instanceof RuntimeException)
				throw (RuntimeException) t;
			if(t instanceof Error)
				throw (Error) t;
			throw new RuntimeException(t);
		}
	}
	
	public static void uncheckedRun(ThrowingRunnable task) {
		try {
			task.run();
		} catch(Throwable t) {
			throw uncheckedHelper.uncheckedThrow(t);
		}
	}
	
	public static <T> T checkedCall(ThrowingCallable<T> task) {
		try {
			return task.call();
		} catch(Throwable t) {
			if(t instanceof RuntimeException)
				throw (RuntimeException) t;
			if(t instanceof Error)
				throw (Error) t;
			throw new RuntimeException(t);
		}
	}
	
	public static <T> T uncheckedRun(ThrowingCallable<T> task) {
		try {
			return task.call();
		} catch(Throwable t) {
			throw uncheckedHelper.uncheckedThrow(t);
		}
	}
	
	public static void quietlyRun(ThrowingRunnable task) {
		try {
			task.run();
		} catch(Throwable t) {
			if(t instanceof Error)
				throw (Error) t;
		}
	}
	
	private static class UncheckedHelper<T extends Throwable> {
		@SuppressWarnings("unchecked")
		public T uncheckedThrow(Throwable t) throws T {
			throw (T) t;
		}
	}
	
	private static final UncheckedHelper<RuntimeException> uncheckedHelper = new UncheckedHelper<>();

	private static final Map<String, Object> getterHandles = new ConcurrentHashMap<>();
	
	public static <T> T invokeGetter(Class<T> rtype, String getterName, Object invokee) throws NoSuchMethodException, IllegalAccessException {
		String handleKey = invokee.getClass().getName() + ":" + getterName;
		Object gh = getterHandles.get(handleKey);
		MethodHandle mh;
		if(gh == null) {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodType mt = MethodType.methodType(rtype);
			try {
				mh = lookup.findVirtual(invokee.getClass(), getterName, mt);
			} catch(NoSuchMethodException | IllegalAccessException e) {
				getterHandles.put(handleKey, e);
				throw e;
			}
		} else if(gh instanceof MethodHandle) {
			mh = (MethodHandle) gh;
		} else if(gh instanceof NoSuchMethodException) {
			throw (NoSuchMethodException) gh;
		} else if(gh instanceof IllegalAccessException) {
			throw (IllegalAccessException) gh;
		} else
			throw new IllegalStateException("unhandled gh=" + gh);
		
		return checkedCall(() -> rtype.cast(mh.invoke(invokee)));
	}
	
	public static <T> T invokeGetter(Class<T> rtype, String getterName, Object invokee, T defaultValue) {
		String handleKey = invokee.getClass().getName() + ":" + getterName;
		Object gh = getterHandles.get(handleKey);
		MethodHandle mh;
		if(gh == null) {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodType mt = MethodType.methodType(rtype);
			try {
				mh = lookup.findVirtual(invokee.getClass(), getterName, mt);
			} catch(NoSuchMethodException | IllegalAccessException e) {
				getterHandles.put(handleKey, e);
				return defaultValue;
			}
		} else if(gh instanceof MethodHandle) {
			mh = (MethodHandle) gh;
		} else if(gh instanceof NoSuchMethodException) {
			return defaultValue;
		} else if(gh instanceof IllegalAccessException) {
			return defaultValue;
		} else
			throw new IllegalStateException("unhandled gh=" + gh);
		
		return checkedCall(() -> rtype.cast(mh.invoke(invokee)));
	}
	
}
