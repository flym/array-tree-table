/** Created by flym at 2014/4/28 */
package com.iflym.table.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** @author flym */
public class ClassUtils {

    /** class.forName的无异常版 */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> forName(String clazz) {
        try{
            return (Class<T>) Class.forName(clazz);
        } catch(ClassNotFoundException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /** 对指定的类进行初始化 */
    @SuppressWarnings("unchecked")
    public static <T> T newInstanceUseConstructor(Class<T> clazz) {
        try{
            Constructor rConstructor = clazz.getDeclaredConstructor();
            if(!rConstructor.isAccessible())
                rConstructor.setAccessible(true);
            return (T) rConstructor.newInstance();
        } catch(NoSuchMethodException | InvocationTargetException | IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
