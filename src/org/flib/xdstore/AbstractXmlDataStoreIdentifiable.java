package org.flib.xdstore;


public abstract class AbstractXmlDataStoreIdentifiable implements IXmlDataStoreIdentifiable {
	
	private String	dataStoreId;

	@Override
	public String getDataStoreId() {
		return dataStoreId;
	}

	@Override
	public void setDataStoreId(final String dataStoreId) {
		this.dataStoreId = dataStoreId;
	}

}
