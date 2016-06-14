package com.iflym.table.fastjson;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.MapDeserializer;
import com.iflym.table.util.ClassUtils;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 用于创建相应的rowIndex或columnIndex
 * Created by flym on 6/7/2016.
 */
public class IndexMapDeserializer extends MapDeserializer {
    private Class keyClass;
    private Class comparatorClass;

    public IndexMapDeserializer(Class keyClass, Class comparatorClass) {
        this.keyClass = keyClass;
        this.comparatorClass = comparatorClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<Object, Object> createMap(Type type) {
        return new TreeMap<>((Comparator) ClassUtils.newInstanceUseConstructor(comparatorClass));
    }

    @SuppressWarnings("unchecked")
    protected Object deserialze(DefaultJSONParser parser, Type type, Object fieldName, Map map) {
        return parseMap(parser, map, keyClass, Integer.class, fieldName);
    }
}
