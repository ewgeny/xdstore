package org.flib.xdstore.transaction;

import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.XmlDataStoreObjectIdField;
import org.flib.xdstore.XmlDataStorePolicy;
import org.flib.xdstore.serialization.IXmlDataStoreIOFactory;
import org.flib.xdstore.serialization.XmlDataStoreDefaultIOFactory;
import org.flib.xdstore.trigger.XmlDataStoreTriggerManager;

public class XmlDataStoreResourcesManager {

	private final String                             folder;

	private final XmlDataStoreTriggerManager         triggersManager;

	private final Map<Class<?>, XmlDataStorePolicy>  policies;

	private final Map<String, IXmlDataStoreResource> resources;

	private final Map<String, Long>                  locks;

	private IXmlDataStoreIOFactory                   factory;

	private final int                                fragmentSize;

	public XmlDataStoreResourcesManager(final String folder, final XmlDataStoreTriggerManager triggersManager, final Map<Class<?>, XmlDataStorePolicy> policies,
	        final int fragmentSize) {
		this.folder = folder;
		this.triggersManager = triggersManager;
		this.policies = policies;
		this.resources = new TreeMap<String, IXmlDataStoreResource>();
		this.locks = new HashMap<String, Long>();
		this.factory = new XmlDataStoreDefaultIOFactory();
		this.fragmentSize = fragmentSize;
	}

	public void setIOFactory(final IXmlDataStoreIOFactory factory) {
		this.factory = factory;
	}

	public synchronized XmlDataStoreIndexResource lockIndexResource(final Class<? extends IXmlDataStoreIdentifiable> cl, final XmlDataStoreTransaction transaction) {
		XmlDataStoreIndexResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final String resourceId = folder + "/" + cl.getSimpleName() + "-index";
			resource = (XmlDataStoreIndexResource) resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreIndexResource(this, triggersManager, resourceId, policies, factory, fragmentSize));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return resource;
	}

	synchronized XmlDataStoreResource lockResource(final String resourceId, final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = resources.get(resourceId);
		if (resource == null) {
			resources.put(resourceId, resource = new XmlDataStoreResource(this, triggersManager, resourceId, policies, factory));
		}
		if (!transaction.isResourceRegistred(resource)) {
			transaction.registerResource(resource);
			lockResource(resource);
		}
		return (XmlDataStoreResource) resource;
	}

	synchronized XmlDataStoreResource lockResource(final Class<? extends IXmlDataStoreIdentifiable> cl, final String id, final XmlDataStoreTransaction transaction) {
		final String resourceId = folder + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + id;
		IXmlDataStoreResource resource = resources.get(resourceId);
		if (resource == null) {
			resources.put(resourceId, resource = new XmlDataStoreResource(this, triggersManager, resourceId, policies, factory));
		}
		if (!transaction.isResourceRegistred(resource)) {
			transaction.registerResource(resource);
			lockResource(resource);
		}
		return (XmlDataStoreResource) resource;
	}

	public synchronized XmlDataStoreResource lockReferencesResource(final Class<? extends IXmlDataStoreIdentifiable> cl, final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final String resourceId = folder + "/" + cl.getSimpleName();
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreResource(this, triggersManager, resourceId, policies, true, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreResource) resource;
	}

	public synchronized XmlDataStoreResource lockClassResource(final Class<? extends IXmlDataStoreIdentifiable> cl, final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final String resourceId = folder + "/" + cl.getSimpleName();
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreResource(this, triggersManager, resourceId, policies, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreResource) resource;
	}

	public synchronized <T extends IXmlDataStoreIdentifiable> XmlDataStoreResource lockObjectResource(final T object, final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final Class<? extends IXmlDataStoreIdentifiable> cl = object.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final String resourceId = folder + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + object.getDataStoreId();
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreResource(this, triggersManager, resourceId, policies, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreResource) resource;
	}

	public synchronized XmlDataStoreResource lockObjectResource(final Class<? extends IXmlDataStoreIdentifiable> cl, final String id, final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final String resourceId = folder + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + id;
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreResource(this, triggersManager, resourceId, policies, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreResource) resource;
	}

	public synchronized XmlDataStoreAnnotatedIndexResource lockAnnotatedIndexResource(final Class<?> cl, final XmlDataStoreObjectIdField field,
	        final XmlDataStoreTransaction transaction) {
		XmlDataStoreAnnotatedIndexResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final String resourceId = folder + "/" + cl.getSimpleName() + "-index";
			resource = (XmlDataStoreAnnotatedIndexResource) resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreAnnotatedIndexResource(this, triggersManager, resourceId, field, policies, factory, fragmentSize));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return resource;
	}

	synchronized XmlDataStoreAnnotatedResource lockAnnotatedResource(final String resourceId, final XmlDataStoreObjectIdField field, final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = resources.get(resourceId);
		if (resource == null) {
			resources.put(resourceId, resource = new XmlDataStoreAnnotatedResource(this, triggersManager, resourceId, field, policies, factory));
		}
		if (!transaction.isResourceRegistred(resource)) {
			transaction.registerResource(resource);
			lockResource(resource);
		}
		return (XmlDataStoreAnnotatedResource) resource;
	}

	synchronized XmlDataStoreAnnotatedResource lockAnnotatedResource(final Class<?> cl, final XmlDataStoreObjectIdField field, final Object id,
	        final XmlDataStoreTransaction transaction) {
		final String resourceId = folder + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + id;
		IXmlDataStoreResource resource = resources.get(resourceId);
		if (resource == null) {
			resources.put(resourceId, resource = new XmlDataStoreAnnotatedResource(this, triggersManager, resourceId, field, policies, factory));
		}
		if (!transaction.isResourceRegistred(resource)) {
			transaction.registerResource(resource);
			lockResource(resource);
		}
		return (XmlDataStoreAnnotatedResource) resource;
	}

	public synchronized XmlDataStoreAnnotatedResource lockAnnotatedReferencesResource(final Class<?> cl, final XmlDataStoreObjectIdField field,
	        final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final String resourceId = folder + "/" + cl.getSimpleName();
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreAnnotatedResource(this, triggersManager, resourceId, field, policies, true, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreAnnotatedResource) resource;
	}

	public synchronized XmlDataStoreAnnotatedResource lockAnnotatedClassResource(final Class<?> cl, final XmlDataStoreObjectIdField field, final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final String resourceId = folder + "/" + cl.getSimpleName();
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreAnnotatedResource(this, triggersManager, resourceId, field, policies, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreAnnotatedResource) resource;
	}

	public synchronized <T> XmlDataStoreAnnotatedResource lockAnnotatedObjectResource(final T object, final XmlDataStoreObjectIdField field,
	        final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final Class<?> cl = object.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final String resourceId = folder + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + field.getObjectId(object);
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreAnnotatedResource(this, triggersManager, resourceId, field, policies, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreAnnotatedResource) resource;
	}

	public synchronized XmlDataStoreAnnotatedResource lockAnnotatedObjectResource(final Class<?> cl, final XmlDataStoreObjectIdField field, final Object id,
	        final XmlDataStoreTransaction transaction) {
		IXmlDataStoreResource resource = null;
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final String resourceId = folder + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + id;
			resource = resources.get(resourceId);
			if (resource == null) {
				resources.put(resourceId, resource = new XmlDataStoreAnnotatedResource(this, triggersManager, resourceId, field, policies, factory));
			}
			if (!transaction.isResourceRegistred(resource)) {
				transaction.registerResource(resource);
				lockResource(resource);
			}
		}
		return (XmlDataStoreAnnotatedResource) resource;
	}

	private void lockResource(final IXmlDataStoreResource resource) {
		final String resourceId = resource.getResourceId();
		final Long counter = locks.get(resourceId);
		locks.put(resourceId, counter == null ? Long.valueOf(1) : Long.valueOf(counter.longValue() + 1));
	}

	synchronized void releaseResource(final IXmlDataStoreResource resource) {
		final String resourceId = resource.getResourceId();
		final Long counter = locks.get(resourceId);
		if (counter.longValue() == 1) {
			locks.remove(resourceId);
			resources.remove(resourceId);
		} else {
			locks.put(resourceId, Long.valueOf(counter.longValue() - 1));
		}
	}
}
