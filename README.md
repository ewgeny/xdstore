xdstorage-2.0.0
=============

Changes Java XML storage for JavaBeans
-----------------------------------------------

* Refactored and restructured all code
* Implemented change IO format (you can change data format in files)
	- Just implement Factory, Writer and Reader
* Implemented change naming resources
    - Just implement NamingService
* Implemented fragmentation for policy ClassObjectsFile
* Implemented triggers for Inserted, Updated, Deleted state object
* Changed IXmlDataStorageIdentifiable - deleted
* Changed AbstractXmlDataStorageIdentifiable - deleted
* Deleted methods for a work with roots
* Deleted callassification objects to annotated and identifiable
* Changed default xml reader and writer
* Added annotation @XmlDataStorageObjectId to tell about field is identifier
	- Mark your field (type of the field must have constructor with one parameter java.lang.String)
	- Make getter and setter for this field
	- Type of the field must implement methods equals() and hashCode()
* Added rolling back for a failed commit (data base return back to valid state)
* Added checking existing object in the storage
