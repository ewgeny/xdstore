package org.flib.xdstore.triggers;

import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.trigger.IXmlDataStoreTrigger;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;

public class XdUniverseInsertTrigger implements IXmlDataStoreTrigger<XdUniverse> {

	@Override
	public XmlDataStoreTriggerType getType() {
		return XmlDataStoreTriggerType.Insert;
	}

	@Override
	public Class<XdUniverse> getClazz() {
		return XdUniverse.class;
	}

	@Override
	public void perform(final XdUniverse object) {
		System.out.println("INSERTED XdUnivere " + object.getDataStoreId());
	}

}
