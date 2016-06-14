package com.iflym.table.util;

import com.google.common.collect.Table;
import com.iflym.table.ArrayTreeTable;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 相应处理工具,用于支持相应的测试工作
 * Created by flym on 6/14/2016.
 */
public class TableUtils {
    private static final int columnStart = 50;
    private static final int columnEnd = 70;
    private static final int rowStart = 0;
    private static final int rowEnd = 20;

    private static final Field innerVField = FieldUtils.getDeclaredField(ArrayTreeTable.class, "innerV", true);

    /** 使用内部的value来计算长度信息(而不是原生table实现) */
    public static int sizeUsingValue(ArrayTreeTable table) {
        if(table.isEmpty())
            return 0;

        Object[][] innerV = (Object[][]) FieldUtils.getField(innerVField, table);

        int i = 0;
        for(Object[] vv : innerV) {
            for(Object v : vv)
                if(v != null)
                    i++;
        }

        return i;
    }

    /** 用于随机填充相应的表格 */
    public static void fillManyRandom(Table<String, String, String> table) {
        List<Integer> rowList = IntStream.range(rowStart, rowEnd).mapToObj(t -> t).collect(Collectors.toList());
        List<Integer> columnList = IntStream.range(columnStart, columnEnd).mapToObj(t -> t).collect(Collectors.toList());
        Collections.shuffle(rowList);
        Collections.shuffle(columnList);

        for(Integer i : rowList) {
            for(Integer k : columnList) {
                table.put(String.valueOf(i), String.valueOf(k), i + "_" + k);
            }
        }
    }

    /** 随机填充指定次数表格 */
    public static void fillRandom(Table<String, String, String> table, int times) {
        for(int i = 0; i < times; i++) {
            int row = rowStart + (int) (Math.random() * 20);
            int column = columnStart + (int) (Math.random() * 20);
            table.put(String.valueOf(row), String.valueOf(column), row + "_" + column);
        }
    }

    /** 随机填充整数表格 */
    public static void fillIntRandom(Table<Integer, Integer, Integer> table, int times) {
        for(int i = 0; i < times; i++) {
            int row = rowStart + (int) (Math.random() * 20);
            int column = columnStart + (int) (Math.random() * 20);
            table.put(row, column, row * 100 + column);
        }
    }

    /** 随机清除数据格 */
    public static void cleanRandom(Table<String, String, String> table, int times) {
        //总共有400个元素,这里清除times次
        for(int i = 0; i < times; i++) {
            int row = rowStart + (int) (Math.random() * 20);
            int column = columnStart + (int) (Math.random() * 20);
            table.remove(String.valueOf(row), String.valueOf(column));
        }
    }
}
