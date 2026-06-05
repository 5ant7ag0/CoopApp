package com.cooperativa.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotacion personalizada para restringir el acceso a endpoints de administracion global.
 * Peticiones a metodos o controladores anotados con esto seran rechazadas a menos que
 * el usuario sea SUPER_ADMIN_SAAS.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SuperAdminOnly {
}
