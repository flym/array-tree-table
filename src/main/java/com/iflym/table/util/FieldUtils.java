package com.iflym.table.util;

import java.lang.reflect.Field;

/**
 * 字段工具类,快速访问字段和获取 copy自common lang3和spring
 * Created by flym on 6/14/2016.
 */
public class FieldUtils {
    /** copy 自 org.apache.commons.lang3.reflect.FieldUtils */
    public static Field getDeclaredField(final Class<?> cls, final String fieldName, final boolean forceAccess) {
        try{
            // only consider the specified class by using getDeclaredField()
            final Field field = cls.getDeclaredField(fieldName);
            if(!field.isAccessible()) {
                if(forceAccess) {
                    field.setAccessible(true);
                } else {
                    return null;
                }
            }
            return field;
        } catch(final NoSuchFieldException e) { // NOPMD
            // ignore
        }
        return null;
    }

    /** copy自 org.springframework.util.ReflectionUtils */
    public static Object getField(Field field, Object target) {
        try{
            return field.get(target);
        } catch(IllegalAccessException ex) {
            throw new IllegalStateException(
                    "Unexpected reflection exception - " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }
}