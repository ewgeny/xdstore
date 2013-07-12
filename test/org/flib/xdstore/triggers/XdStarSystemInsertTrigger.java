package org.flib.xdstore.triggers;

import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.trigger.IXmlDataStoreTrigger;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;


public class XdStarSystemInsertTrigger implements IXmlDataStoreTrigger<XdStarSystem> {

	@Override
	public XmlDataStoreTriggerType getType() {
		return XmlDataStoreTriggerType.Insert;
	}

	@Override
	public Class<XdStarSystem> getClazz() {
		return XdStarSystem.class;
	}

	@Override
	public void perform(final XdStarSystem object) {
		System.out.println("INSERTED XdStarSystem " + object.getDataStoreId());
	}

}
