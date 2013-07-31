xdstore-1.7.1
=============

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
* Added annotation @XmlDataStoreObjectId to tell about field is identifier
	- Mark your field (this type must have constructor with one parameter java.lang.String)
	- Make getter and setter for this field
	- Implement methods equals() and hashCode()
* Added group methods for a work with 'Annotated' classes
* Added rolling back for a failed commit (data base return back to valid state)

Fixed bugs
===========
* Not all public getters/setters used by serialization.
* Fixed bug in the default XML reader of this tool
