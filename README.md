xdstore-1.5
===========

Changes Java XML store for JavaBeans
-----------------------------------------------

* Implemented change IO format (you can change data format in files)
	- Just implement Factory, Writer and Reader
* Implemented fragmentation for policy ClassObjectsFile
* Implemented triggers for Inserted, Updated, Deleted state object
* Changed IXmlDataStoreIdentifiable (methods get/set Id changed to get/set DataStoreId)
* Added abstract class AbstractXmlDataStoreIdentifiable
* Deleted methods for a work with roots
* Changed default xml reader and writer

Bugs
===========
Not all public getters/setters used by serialization. (fixed xdstore-1.6)
