package org.flib.xdstore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If one field of the class described this annotation, this must have two
 * methods for a work with this field (getter and setter). And the class
 * identifier object must have constructor with one parameter java.lang.String,
 * and replace the method toString().
 * 
 * @author Евгений
 * 
 */
@Target(value = ElementType.FIELD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XmlDataStoreObjectId {

}
