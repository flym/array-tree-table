package com.iflym.table.fastjson;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.DefaultFieldDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.FieldInfo;
import com.google.common.collect.Maps;
import com.iflym.table.ArrayTreeTable;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * 用于处理下标的反序列化
 * Created by flym on 6/7/2016.
 */
public class IndexFieldDeserializer extends DefaultFieldDeserializer {
    private Map<Tuple2, ObjectDeserializer> deserializerMap = Maps.newConcurrentMap();
    private boolean rowIndex;

    public IndexFieldDeserializer(ParserConfig parserConfig, Class<?> clazz, FieldInfo fieldInfo, boolean rowIndex) {
        super(parserConfig, clazz, fieldInfo);
        this.rowIndex = rowIndex;
    }

    public void parseField(DefaultJSONParser parser, Object object, Type objectType, Map<String, Object> fieldValues) {
        val table = (ArrayTreeTable) object;
        val keyClass = rowIndex ? table.getRowClass() : table.getColumnClass();
        val keyComparatorClass = rowIndex ? table.getRowComparatorClass() : table.getColumnComparatorClass();
        ObjectDeserializer fieldValueDeserilizer = deserializerMap.computeIfAbsent(new Tuple2(keyClass, keyComparatorClass),
                k -> new IndexMapDeserializer(keyClass, keyComparatorClass));

        //---------------------------- 复制父类的逻辑 start ------------------------------//
        Object value = fieldValueDeserilizer.deserialze(parser, fieldInfo.fieldType, fieldInfo.name);
        if(parser.getResolveStatus() == DefaultJSONParser.NeedToResolve) {
            DefaultJSONParser.ResolveTask task = parser.getLastResolveTask();
            task.fieldDeserializer = this;
            task.ownerContext = (parser.getContext());
            parser.setResolveStatus(DefaultJSONParser.NONE);
        } else {
            setValue(object, value);
        }
        //---------------------------- 复制父类的逻辑 end ------------------------------//
    }

    @Override
    public int getFastMatchToken() {
        return JSONToken.LBRACE;
    }

    @EqualsAndHashCode
    @RequiredArgsConstructor
    private class Tuple2 {
        private final Class class1;
        private final Class class2;
    }
}
