package org.flib.xdstore.triggers;

import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.trigger.IXmlDataStoreTrigger;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;

public class XdUniverseUpdateTrigger implements IXmlDataStoreTrigger<XdUniverse> {

	@Override
	public XmlDataStoreTriggerType getType() {
		return XmlDataStoreTriggerType.Update;
	}

	@Override
	public Class<XdUniverse> getClazz() {
		return XdUniverse.class;
	}

	@Override
	public void perform(final XdUniverse object) {
		System.out.println("UPDATED XdUniverse " + object.getDataStoreId());
	}

}
