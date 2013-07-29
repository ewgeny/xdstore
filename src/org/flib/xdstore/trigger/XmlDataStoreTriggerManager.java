package org.flib.xdstore.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flib.xdstore.IXmlDataStoreIdentifiable;

public class XmlDataStoreTriggerManager {

	private Map<Class<?>, List<IXmlDataStoreTrigger<?>>> triggers;

	public XmlDataStoreTriggerManager() {
		triggers = new HashMap<Class<?>, List<IXmlDataStoreTrigger<?>>>();
	}

	@SuppressWarnings("unchecked")
	public <T> void registerTrigger(final IXmlDataStoreTrigger<T> trigger) {
		List<IXmlDataStoreTrigger<?>> list;
		synchronized (this) {
			list = triggers.get(trigger.getClazz());
			if (list == null) {
				triggers.put((Class<?>) trigger.getClazz(), list = new ArrayList<IXmlDataStoreTrigger<?>>());
			}
		}
		synchronized (list) {
			list.add((IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>) trigger);
		}
	}

	public <T> void performTriggers(final XmlDataStoreTriggerType type, final T object) {
		if (type == XmlDataStoreTriggerType.Insert) {
			performInsertTriggers(object);
		} else if (type == XmlDataStoreTriggerType.Update) {
			performUpdateTriggers(object);
		} else if (type == XmlDataStoreTriggerType.Delete) {
			performDeleteTriggers(object);
		}
	}

	@SuppressWarnings("unchecked")
    public <T> void performInsertTriggers(final T object) {
		List<IXmlDataStoreTrigger<?>> list;
		synchronized (this) {
			list = triggers.get(object.getClass());
		}
		if (list != null) {
			synchronized (list) {
				for (final IXmlDataStoreTrigger<?> trigger : list) {
					if (trigger.getType() == XmlDataStoreTriggerType.Insert) {
						((IXmlDataStoreTrigger<T>)trigger).perform(object);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
    public <T> void performUpdateTriggers(final T object) {
		List<IXmlDataStoreTrigger<?>> list;
		synchronized (this) {
			list = triggers.get(object.getClass());
		}
		if (list != null) {
			synchronized (list) {
				for (final IXmlDataStoreTrigger<?> trigger : list) {
					if (trigger.getType() == XmlDataStoreTriggerType.Update) {
						((IXmlDataStoreTrigger<T>)trigger).perform(object);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
    public <T> void performDeleteTriggers(final T object) {
		List<IXmlDataStoreTrigger<?>> list;
		synchronized (this) {
			list = triggers.get(object.getClass());
		}
		if (list != null) {
			synchronized (list) {
				for (final IXmlDataStoreTrigger<?> trigger : list) {
					if (trigger.getType() == XmlDataStoreTriggerType.Delete) {
						((IXmlDataStoreTrigger<T>)trigger).perform(object);
					}
				}
			}
		}
	}
}
