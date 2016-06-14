package com.iflym.table.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.collect.Maps;
import com.iflym.table.ArrayTreeTable;
import com.iflym.table.util.ClassUtils;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 用于实现在jackson环境下ArrayTreeTable的反序列化
 * Created by flym on 6/13/2016.
 */
public class ArrayTreeTableDeserializer extends StdDeserializer<ArrayTreeTable> {
    /** 用于创建indexMap的处理器 */
    private static Map<Class, JsonDeserializer> indexDeserializerMap = Maps.newHashMap();
    /** 用于处理innerV的处理器 */
    private static Map<Class, JsonDeserializer> valueDeserializerMap = Maps.newHashMap();
    /** 用于处理bits位的处理器 */
    private static JsonDeserializer bitsDeserializer;

    /** 当前使用的mapper处理器 */
    private static ObjectMapper objectMapper;
    /** 用于创建相应的反序列化器的上下文 */
    private transient static DefaultDeserializationContext usedContext;

    /**
     * 设置并绑定相应的初始反序列化器处理工厂
     * 因为不同的处理上下文中所使用的反序列化器是可以相通的,因此这里直接使用初始化的context来创建相应的处理器
     * 同时使用全局的缓存来缓存相应的实例,以在后面再次使用
     */
    public static void setObjectMapper(ObjectMapper objectMapper) {
        ArrayTreeTableDeserializer.objectMapper = objectMapper;
        DefaultDeserializationContext context = (DefaultDeserializationContext) objectMapper.getDeserializationContext();
        usedContext = context.createInstance(objectMapper.getDeserializationConfig(), null, objectMapper.getInjectableValues());

        bitsDeserializer = createBitsDeserializer();
    }

    private static JsonDeserializer _create(JavaType javaType) {
        try{
            return usedContext.findRootValueDeserializer(javaType);
        } catch(JsonMappingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static JsonDeserializer createIndexMapDeserializer(Class<?> keyClass) {
        MapType javaType = objectMapper.getTypeFactory().constructMapType(TreeMap.class, keyClass, Integer.class);
        return _create(javaType);
    }

    private static JsonDeserializer createValueDeserializer(Class<?> valueClass) {
        Class vClass = ClassUtils.forName("[[L" + valueClass.getName() + ";");
        return _create(objectMapper.getTypeFactory().constructType(vClass));
    }

    private static JsonDeserializer createBitsDeserializer() {
        return _create(objectMapper.getTypeFactory().constructArrayType(long.class));
    }

    public ArrayTreeTableDeserializer() {
        super(ArrayTreeTable.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayTreeTable<?, ?, Object> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        p.nextToken();
        ArrayTreeTable<?, ?, Object> table = new ArrayTreeTable();

        for(JsonToken t = p.getCurrentToken(); t != JsonToken.END_OBJECT; t = p.nextToken()) {
            if(t != JsonToken.FIELD_NAME)
                continue;

            String name = p.getCurrentName();
            p.nextToken();
            switch(name) {
                case "rowClass":
                    table.setRowClass(ClassUtils.forName(p.getText()));
                    break;
                case "columnClass":
                    table.setColumnClass(ClassUtils.forName(p.getText()));
                    break;
                case "valueClass":
                    table.setValueClass(ClassUtils.forName(p.getText()));
                    break;
                case "rowComparatorClass":
                    table.setRowComparatorClass(ClassUtils.forName(p.getText()));
                    break;
                case "columnComparatorClass":
                    table.setColumnComparatorClass(ClassUtils.forName(p.getText()));
                    break;
                case "rowIndex": {
                    TreeMap<?, Integer> rowIndexMap = Maps.newTreeMap(ClassUtils.newInstanceUseConstructor(table.getRowComparatorClass()));
                    table.setRowIndex((TreeMap) rowIndexMap);
                    JsonDeserializer rowDeserializer = indexDeserializerMap.computeIfAbsent(table.getRowClass(),
                            ArrayTreeTableDeserializer::createIndexMapDeserializer);
                    rowDeserializer.deserialize(p, ctxt, rowIndexMap);
                }
                break;
                case "columnIndex": {
                    TreeMap<?, Integer> columnIndexMap = Maps.newTreeMap(ClassUtils.newInstanceUseConstructor(table.getColumnComparatorClass()));
                    table.setColumnIndex((TreeMap) columnIndexMap);
                    JsonDeserializer columnDeserializer = indexDeserializerMap.computeIfAbsent(table.getColumnClass(),
                            ArrayTreeTableDeserializer::createIndexMapDeserializer);
                    columnDeserializer.deserialize(p, ctxt, columnIndexMap);
                }
                break;
                case "innerV": {
                    JsonDeserializer valueDeserializer = valueDeserializerMap.computeIfAbsent(table.getValueClass(),
                            ArrayTreeTableDeserializer::createValueDeserializer);
                    Object obj = valueDeserializer.deserialize(p, ctxt);
                    table.setInnerV((Object[][]) obj);
                }
                break;
                case "bits": {
                    long[] bits = (long[]) bitsDeserializer.deserialize(p, ctxt);
                    table.setBits(bits);
                }
                break;
                default:
                    break;
            }
        }

        return table;
    }
}
