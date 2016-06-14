package com.iflym.table;

import com.iflym.table.util.FastJsonUtils;
import com.iflym.table.util.JacksonUtils;
import com.iflym.table.util.TableUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Comparator;

/**
 * 对table的相应测试
 * Created by flym on 6/14/2016.
 */
public class ArrayTreeTableTest {

    @SuppressWarnings("unchecked")
    private ArrayTreeTable<String, String, String> createTable() {
        ArrayTreeTable<String, String, String> table = new ArrayTreeTable<>(String.class, String.class, String.class,
                (Class) String.CASE_INSENSITIVE_ORDER.getClass(), (Class) String.CASE_INSENSITIVE_ORDER.getClass());
        table.init();

        return table;
    }

    private static class IntCmp implements Comparator<Integer> {
        @Override
        public int compare(Integer o1, Integer o2) {
            return Integer.compare(o1, o2);
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayTreeTable<Integer, Integer, Integer> createIntTable() {
        ArrayTreeTable<Integer, Integer, Integer> table = new ArrayTreeTable(int.class, int.class, int.class,
                IntCmp.class, IntCmp.class);
        table.init();

        return table;
    }

    /** 测试其能正常的进行工作 */
    @Test
    @SuppressWarnings("unchecked")
    public void testNormalWork() {
        ArrayTreeTable<String, String, String> table = createTable();

        //进行100次随机写入和删除测试
        for(int i = 0; i < 100; i++) {
            table.clear();
            TableUtils.fillManyRandom(table);
            TableUtils.cleanRandom(table, 600);//进行600次随机删除操作
            Assert.assertEquals(table.size(), TableUtils.sizeUsingValue(table));
        }
    }

    /** 测试在基本类型下的各个类型是否正常工作 */
    @Test
    public void testPrimitive() {
        ArrayTreeTable<Integer, Integer, Integer> table = createIntTable();
        TableUtils.fillIntRandom(table, 200);
        Assert.assertEquals(table.size(), TableUtils.sizeUsingValue(table));

        //序列化和反序列化
        //fastjson
        String str = FastJsonUtils.toJson(table);
        ArrayTreeTable deTable = FastJsonUtils.parse(str);
        deTable.init();
        Assert.assertEquals(table, deTable);

        //jackson
        str = FastJsonUtils.toJson(table);
        deTable = FastJsonUtils.parse(str);
        deTable.init();
        Assert.assertEquals(table, deTable);
    }

    /** 测试使用fastjson序列化和反序列化 */
    @Test
    @SuppressWarnings("unchecked")
    public void testFastJsonSerial() {
        ArrayTreeTable<String, String, String> table = createTable();

        //进行50次随机填充和序列化和反序列化处理
        for(int i = 0; i < 50; i++) {
            table.clear();
            TableUtils.fillRandom(table, 200);

            String str = FastJsonUtils.toJson(table);
            ArrayTreeTable deTable = FastJsonUtils.parse(str);
            deTable.init();//必要的初始化
            Assert.assertEquals(table, deTable);
        }
    }

    /** 测试使用jackson序列化和反序列化 */
    @Test
    @SuppressWarnings("unchecked")
    public void testJacksonSerial() throws IOException {
        ArrayTreeTable<String, String, String> table = createTable();
        table.init();

        //进行50次随机填充和序列化和反序列化处理
        for(int i = 0; i < 50; i++) {
            table.clear();
            TableUtils.fillRandom(table, 200);

            String str = JacksonUtils.toJson(table);
            ArrayTreeTable deTable = JacksonUtils.parse(str);
            deTable.init();//必要的初始化
            Assert.assertEquals(table, deTable);
        }
    }
}
