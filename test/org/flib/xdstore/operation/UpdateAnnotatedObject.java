package org.flib.xdstore.operation;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdAnnotatedObject;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class UpdateAnnotatedObject implements Runnable {

	private final XmlDataStore store;

	public UpdateAnnotatedObject(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		final Random rand = new Random();
		System.out.println("UpdateAnnotatedObject Started");
		final XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			final Map<Object, XdAnnotatedObject> objects = store.loadAnnotatedObjects(XdAnnotatedObject.class);
			if (objects.size() > 0) {
				int number = Math.abs(rand.nextInt()) % objects.size();
				final Iterator<Entry<Object, XdAnnotatedObject>> it = objects.entrySet().iterator();
				XdAnnotatedObject object = null;
				for (int i = 0; i < number; ++i) {
					object = it.next().getValue();
				}
				if (object != null)
					store.updateAnnotatedObject(object);
			}

			tx.commit();
			System.out.println("UpdateAnnotatedObject Commited");
		} catch (final XmlDataStoreException e) {
			e.printStackTrace();
			tx.rollback();
			System.out.println("UpdateAnnotatedObject Rolledback");
		}
	}

}
