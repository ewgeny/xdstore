package org.flib.xdstore.transaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.flib.xdstore.IXmlDataStoreIdentifiable;


public class XmlDataStoreIndexObject implements IXmlDataStoreIdentifiable {
	
	private String	id;	// this field contains resource identifier
	
	private Map<String, IXmlDataStoreIdentifiable>	references	=	new ConcurrentHashMap<String, IXmlDataStoreIdentifiable>();

	@Override
	public String getDataStoreId() {
		return id;
	}

	@Override
	public void setDataStoreId(final String id) {
		this.id = id;
	}
	
    public Map<String, IXmlDataStoreIdentifiable> getReferences() {
    	return references;
    }
	
    public void setReferences(final Map<String, IXmlDataStoreIdentifiable> references) {
    	this.references = references;
    }

	public int getCountReferences() {
		return references.size();
	}
	
	public void addReference(final IXmlDataStoreIdentifiable reference) {
		references.put(reference.getDataStoreId(), reference);
	}
	
	public void removeReference(final IXmlDataStoreIdentifiable reference) {
		references.remove(reference.getDataStoreId());
	}
}
