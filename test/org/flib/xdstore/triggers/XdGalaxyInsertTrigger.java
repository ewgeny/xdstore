package org.flib.xdstore.triggers;

import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.trigger.IXmlDataStoreTrigger;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;


public class XdGalaxyInsertTrigger implements IXmlDataStoreTrigger<XdGalaxy> {

	@Override
	public XmlDataStoreTriggerType getType() {
		return XmlDataStoreTriggerType.Insert;
	}

	@Override
	public Class<XdGalaxy> getClazz() {
		return XdGalaxy.class;
	}

	@Override
	public void perform(final XdGalaxy object) {
		System.out.println("INSERTED XdGalaxy" + object.getId());
	}

}
