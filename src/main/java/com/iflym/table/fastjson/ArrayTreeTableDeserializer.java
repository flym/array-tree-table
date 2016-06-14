package com.iflym.table.fastjson;

import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.FieldDeserializer;
import com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer;
import com.alibaba.fastjson.util.FieldInfo;
import com.iflym.table.ArrayTreeTable;
import com.iflym.table.util.FieldUtils;

import java.lang.reflect.Field;

/**
 * 相应的反序列化器
 * Created by flym on 6/7/2016.
 */
public class ArrayTreeTableDeserializer extends JavaBeanDeserializer {

    public ArrayTreeTableDeserializer(ParserConfig parserConfig) {
        super(parserConfig, ArrayTreeTable.class, ArrayTreeTable.class);

        //从当前的实现来看,需要重新修改父类中的字段反序列化器
        Field sortedFieldDeserializersField = FieldUtils.getDeclaredField(JavaBeanDeserializer.class, "sortedFieldDeserializers", true);
        Field fieldDeserializersField = FieldUtils.getDeclaredField(JavaBeanDeserializer.class, "fieldDeserializers", true);

        FieldDeserializer[] sortedFieldDeserializers = (FieldDeserializer[]) FieldUtils.getField(sortedFieldDeserializersField, this);
        FieldDeserializer[] fieldDeserializers = (FieldDeserializer[]) FieldUtils.getField(fieldDeserializersField, this);
        replaceSortedFieldDeserializer(parserConfig, sortedFieldDeserializers);
        replaceFieldDeserializersField(fieldDeserializers);
    }

    private void replaceFieldDeserializersField(FieldDeserializer[] fieldDeserializers) {
        //实际上 fieldDeserializers 在父类中并没有什么用处,这里仅是兼容处理,并且此数组部分值还是null值
        for(int i = 0; i < fieldDeserializers.length; i++) {
            FieldDeserializer source = fieldDeserializers[i];
            if(source == null)
                continue;

            FieldDeserializer fieldDeserializer = getFieldDeserializer(source.fieldInfo.name);
            fieldDeserializers[i] = fieldDeserializer;
        }
    }

    /** 替换指定的字段反序列化器 */
    private void replaceSortedFieldDeserializer(ParserConfig parserConfig, FieldDeserializer[] sortedFieldDeserializers) {
        for(int i = 0; i < sortedFieldDeserializers.length; i++) {
            FieldDeserializer deserializer = sortedFieldDeserializers[i];
            FieldInfo fieldInfo = deserializer.fieldInfo;
            FieldDeserializer replace = null;
            switch(fieldInfo.name) {
                case "rowIndex":
                    replace = new IndexFieldDeserializer(parserConfig, clazz, fieldInfo, true);
                    break;
                case "columnIndex":
                    replace = new IndexFieldDeserializer(parserConfig, clazz, fieldInfo, false);
                    break;
                case "innerV":
                    replace = new ValueFieldDeserializer(parserConfig, clazz, fieldInfo);
                    break;
                default:
                    break;
            }

            if(replace != null)
                sortedFieldDeserializers[i] = replace;
        }
    }
}