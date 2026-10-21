package io.github.xdstorage.annotations;

import io.github.xdstorage.XdStoragePolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageObjectPolicy {

    XdStoragePolicy policy() default XdStoragePolicy.StoreWithParentObject;
}
