package com.iflym.table.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Lists;
import com.iflym.table.ArrayTreeTable;
import com.iflym.table.fastjson.ArrayTreeTableDeserializer;

import java.util.List;

/**
 * 用于使用fastjson序列化和反序列化
 * Created by flym on 6/14/2016.
 */
public class FastJsonUtils {

    private static SerializerFeature[] jsonFeaturesNotWriteClassName;

    static {
        //以下为注册相应的反序列化器
        ParserConfig parseConfig = ParserConfig.getGlobalInstance();
        ArrayTreeTableDeserializer tableDeserializer = new ArrayTreeTableDeserializer(parseConfig);
        parseConfig.putDeserializer(ArrayTreeTable.class, tableDeserializer);

        //在序列化时不要写类型信息,因为Table已内置各个类型
        //同时在输出时输出类型会加大json的长度
        List<SerializerFeature> serializerFeatureList = Lists.newArrayList();
        serializerFeatureList.add(SerializerFeature.SkipTransientField);
        serializerFeatureList.add(SerializerFeature.WriteDateUseDateFormat);
        jsonFeaturesNotWriteClassName = serializerFeatureList.toArray(new SerializerFeature[serializerFeatureList.size()]);
    }

    public static String toJson(ArrayTreeTable table) {
        return JSON.toJSONString(table, SerializeConfig.globalInstance, jsonFeaturesNotWriteClassName);
    }

    public static ArrayTreeTable parse(String json) {
        return JSON.parseObject(json, ArrayTreeTable.class);
    }
}
