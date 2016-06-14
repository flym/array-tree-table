package com.iflym.table.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflym.table.ArrayTreeTable;

import java.io.IOException;

/**
 * 使用jackson进行测试
 * Created by flym on 6/14/2016.
 */
public class JacksonUtils {
    private static ObjectMapper objectMapper;

    static {
        //这里需要先注册相应的mapper,以预先初始化必要的信息
        objectMapper = new ObjectMapper();
        com.iflym.table.jackson.ArrayTreeTableDeserializer.setObjectMapper(objectMapper);
    }

    public static String toJson(ArrayTreeTable table) throws JsonProcessingException {
        return objectMapper.writeValueAsString(table);
    }

    public static ArrayTreeTable parse(String json) throws IOException {
        return objectMapper.readValue(json, ArrayTreeTable.class);
    }
}
