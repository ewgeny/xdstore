package org.flib.xdstore.triggers;

import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.trigger.IXmlDataStoreTrigger;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;


public class XdStarSystemDeleteTrigger implements IXmlDataStoreTrigger<XdStarSystem> {

	@Override
	public XmlDataStoreTriggerType getType() {
		return XmlDataStoreTriggerType.Delete;
	}

	@Override
	public Class<XdStarSystem> getClazz() {
		return XdStarSystem.class;
	}

	@Override
	public void perform(final XdStarSystem object) {
		System.out.println("DELETED XdStarSystem " + object.getId());
	}

}
