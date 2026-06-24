package com.cooperativa.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para restringir el acceso a ciertos endpoints según una lista de roles.
 * Puede aplicarse a nivel de clase controladora o de método.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRoles {
    String[] value();
}
