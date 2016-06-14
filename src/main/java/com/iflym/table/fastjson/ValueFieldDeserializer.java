package com.iflym.table.fastjson;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.DefaultFieldDeserializer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.FieldInfo;
import com.iflym.table.ArrayTreeTable;
import com.iflym.table.util.ClassUtils;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * 处理innerV的反序列化
 * Created by flym on 6/7/2016.
 */
public class ValueFieldDeserializer extends DefaultFieldDeserializer {
    private ObjectDeserializer fieldValueDeserilizer;

    public ValueFieldDeserializer(ParserConfig mapping, Class<?> clazz, FieldInfo fieldInfo) {
        super(mapping, clazz, fieldInfo);
    }

    public void parseField(DefaultJSONParser parser, Object object, Type objectType, Map<String, Object> fieldValues) {
        //基本上是复制父类的逻辑,除了在调用具体的字段时传入不同的类型
        ArrayTreeTable table = (ArrayTreeTable) object;
        if(fieldValueDeserilizer == null) {
            fieldValueDeserilizer = parser.getConfig().getDeserializer(fieldInfo);
        }

        Type type = ClassUtils.forName("[[L" + table.getValueClass().getName() + ";");//使用自处理的类型
        Object value = fieldValueDeserilizer.deserialze(parser, type, fieldInfo.name);
        setValue(object, value);
    }
}
