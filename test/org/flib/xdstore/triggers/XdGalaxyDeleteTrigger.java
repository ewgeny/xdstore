package org.flib.xdstore.triggers;

import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.trigger.IXmlDataStoreTrigger;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;


public class XdGalaxyDeleteTrigger implements IXmlDataStoreTrigger<XdGalaxy> {

	@Override
	public XmlDataStoreTriggerType getType() {
		return XmlDataStoreTriggerType.Delete;
	}

	@Override
	public Class<XdGalaxy> getClazz() {
		return XdGalaxy.class;
	}

	@Override
	public void perform(final XdGalaxy object) {
		System.out.println("DELETED XdGalaxy " + object.getId());
	}

}
