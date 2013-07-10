package org.flib.xdstore.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flib.xdstore.IXmlDataStoreIdentifiable;


public class XmlDataStoreTriggerManager {

	private Map<Class<IXmlDataStoreIdentifiable>, List<IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>>>	triggers;
	
	public XmlDataStoreTriggerManager() {
		triggers = new HashMap<Class<IXmlDataStoreIdentifiable>, List<IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>>>();
	}
	
	@SuppressWarnings("unchecked")
    public <T extends IXmlDataStoreIdentifiable> void registerTrigger(final IXmlDataStoreTrigger<T> trigger) {
		List<IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>> list;
		synchronized(this) {
    		list = triggers.get(trigger.getClazz());
    		if(list == null) {
    			triggers.put((Class<IXmlDataStoreIdentifiable>) trigger.getClazz(), list = new ArrayList<IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>>());
    		}
		}
		synchronized (list) {
			list.add((IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>) trigger);
        }
	}
	
	public <T extends IXmlDataStoreIdentifiable> void performTriggers(final XmlDataStoreTriggerType type, final T object) {
		if(type == XmlDataStoreTriggerType.Insert) {
			performInsertTriggers(object);
		} else if(type == XmlDataStoreTriggerType.Update) {
			performUpdateTriggers(object);
		} else if(type == XmlDataStoreTriggerType.Delete) {
			performDeleteTriggers(object);
		}
	}
	
	public <T extends IXmlDataStoreIdentifiable> void performInsertTriggers(final T object) {
		List<IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>> list;
		synchronized (this) {
	        list = triggers.get(object.getClass());
        }
		if(list != null) {
			synchronized (list) {
	            for (final IXmlDataStoreTrigger<IXmlDataStoreIdentifiable> trigger : list) {
	                if(trigger.getType() == XmlDataStoreTriggerType.Insert) {
	                	performTrigger(trigger, object);
	                }
                }
            }
		}
	}
	
	public <T extends IXmlDataStoreIdentifiable> void performUpdateTriggers(final T object) {
		List<IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>> list;
		synchronized (this) {
	        list = triggers.get(object.getClass());
        }
		if(list != null) {
			synchronized (list) {
	            for (final IXmlDataStoreTrigger<IXmlDataStoreIdentifiable> trigger : list) {
	                if(trigger.getType() == XmlDataStoreTriggerType.Update) {
	                	performTrigger(trigger, object);
	                }
                }
            }
		}
	}
	
	public <T extends IXmlDataStoreIdentifiable> void performDeleteTriggers(final T object) {
		List<IXmlDataStoreTrigger<IXmlDataStoreIdentifiable>> list;
		synchronized (this) {
	        list = triggers.get(object.getClass());
        }
		if(list != null) {
			synchronized (list) {
	            for (final IXmlDataStoreTrigger<IXmlDataStoreIdentifiable> trigger : list) {
	                if(trigger.getType() == XmlDataStoreTriggerType.Delete) {
	                	performTrigger(trigger, object);
	                }
                }
            }
		}
	}
	
	private <T extends IXmlDataStoreIdentifiable> void performTrigger(final IXmlDataStoreTrigger<IXmlDataStoreIdentifiable> trigger, final T object) {
		// TODO review this code - maybe need to use ThreadPoolExecutor
    	final Thread thread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				trigger.perform(object);
			}
		});
    	thread.setDaemon(true);
    	thread.start();
	}
}
