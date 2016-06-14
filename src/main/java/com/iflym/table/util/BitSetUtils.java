package com.iflym.table.util;

import java.lang.reflect.Field;
import java.util.BitSet;

/**
 * 用于快速的访问bitSet内部信息
 * Created by flym on 6/6/2016.
 */
public class BitSetUtils {
    private static final Field wordsField = FieldUtils.getDeclaredField(BitSet.class, "words", true);

    /** 获取内部的long数组 */
    public static long[] getLongArray(BitSet bitSet) {
        return (long[]) FieldUtils.getField(wordsField, bitSet);
    }
}