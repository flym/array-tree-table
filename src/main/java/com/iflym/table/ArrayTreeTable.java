package com.iflym.table;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.iflym.table.util.BitSetUtils;
import com.iflym.table.util.ClassUtils;
import lombok.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.util.*;

/**
 * tree化的表,支持按行,按列比较
 * 不允许null key
 * 不允许使用基本类型作为相应的行,列,值类型,因为泛型的原因,在内部处理时会强行转换为相应的包装类型
 * Created by flym on 6/6/2016.
 */
@SuppressWarnings("WeakerAccess")
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode
@ToString(exclude = {"rowMap", "columnMap", "cellSet", "valueCollection"})
@JsonPropertyOrder(value = {"columnClass", "rowClass", "valueClass", "rowComparatorClass", "columnComparatorClass"})
@JsonDeserialize(using = com.iflym.table.jackson.ArrayTreeTableDeserializer.class)
public class ArrayTreeTable<R, C, V> implements Table<R, C, V> {
    //---------------------------- 相应的类型信息 start ------------------------------//
    @NonNull
    @JSONField(ordinal = 1)
    private Class<R> rowClass;

    @NonNull
    @JSONField(ordinal = 1)
    private Class<C> columnClass;

    @NonNull
    @JSONField(ordinal = 3)
    private Class<V> valueClass;

    //---------------------------- 相应的类型信息 end ------------------------------//

    //---------------------------- 比较器类 start ------------------------------//
    @JSONField(ordinal = 4)
    @NonNull
    private Class<Comparator<R>> rowComparatorClass;

    @JSONField(ordinal = 5)
    @NonNull
    private Class<Comparator<C>> columnComparatorClass;

    //---------------------------- 比较器类 end ------------------------------//

    //---------------------------- 实际存储 start ------------------------------//

    /** 位置信息,存储当前下标 */
    @JSONField(ordinal = 6)
    private TreeMap<R, Integer> rowIndex;
    @JSONField(ordinal = 7)
    private TreeMap<C, Integer> columnIndex;

    /** 实际存储的值 */
    @JSONField(ordinal = 8)
    private V[][] innerV;

    /** 存储的位置信息 */
    @JSONField(ordinal = 9)
    private long[] bits;

    //---------------------------- 实际存储 end ------------------------------//

    //---------------------------- 引申对象 start ------------------------------//
    private transient Comparator<? super R> rowComparator;
    private transient Comparator<? super C> columnComparator;
    private transient BitSet bitsSet;
    //---------------------------- 引申对象 end ------------------------------//

    //---------------------------- 过程中使用临时对象 start ------------------------------//
    private transient Map<R, Map<C, V>> rowMap;
    private transient Map<C, Map<R, V>> columnMap;
    private transient Set<Cell<R, C, V>> cellSet;
    private transient Collection<V> valueCollection;

    //---------------------------- 过程中使用临时对象 end ------------------------------//

    /** 必要的初始化,在调用new或者反序列化之后,必须要调用的方法 */
    @SuppressWarnings("unchecked")
    public void init() {
        //强行转换各个类型
        rowClass = _boxClass(rowClass);
        columnClass = _boxClass(columnClass);
        valueClass = _boxClass(valueClass);

        rowComparator = ClassUtils.newInstanceUseConstructor(rowComparatorClass);
        columnComparator = ClassUtils.newInstanceUseConstructor(columnComparatorClass);

        //正向初始化
        if(rowIndex == null)
            rowIndex = Maps.newTreeMap(rowComparator);
        if(columnIndex == null)
            columnIndex = Maps.newTreeMap(columnComparator);
        if(innerV == null)
            innerV = (V[][]) Array.newInstance(valueClass, 0, 0);

        if(bits == null)
            bits = new long[1];
        bitsSet = BitSet.valueOf(bits);
    }

    //---------------------------- 内部各种方法 start ------------------------------//

    @SuppressWarnings("unchecked")
    private static <X> Class<X> _boxClass(Class<?> clazz) {
        if(!clazz.isPrimitive())
            return (Class) clazz;

        switch(clazz.getName()) {
            case "byte":
                return (Class) Byte.class;
            case "short":
                return (Class) Short.class;
            case "char":
                return (Class) Character.class;
            case "boolean":
                return (Class) Boolean.class;
            case "int":
                return (Class) Integer.class;
            case "long":
                return (Class) Long.class;
            case "float":
                return (Class) Float.class;
            case "double":
                return (Class) Double.class;
            default:
                throw new RuntimeException("还没有处理到的基本类型");
        }
    }

    @SuppressWarnings("unchecked")
    private R _r(Object rowKey) {
        return (R) rowKey;
    }

    @SuppressWarnings("unchecked")
    private C _c(Object columnKey) {
        return (C) columnKey;
    }

    @SuppressWarnings("unchecked")
    private V _v(Object value) {
        return (V) value;
    }

    /** 增长行,同时迁移之前相应的下标位 */
    @SuppressWarnings("unchecked")
    private Integer _incRow(R rowKey) {
        int rowSize = rowIndex.size();
        int columnSize = columnIndex.size();

        NavigableMap<R, Integer> tailMap = rowIndex.tailMap(rowKey, false);
        Integer putIndex = tailMap.isEmpty() ? rowSize : tailMap.firstEntry().getValue();

        //后面相应的下标项+1,之前的数据进行移位,然后新放入的数据相应的下标就是最前一位的下标

        //下标移位
        tailMap.entrySet().forEach(t -> t.setValue(t.getValue() + 1));

        //数据移位
        int needMove = rowSize - putIndex;
        @SuppressWarnings("unchecked")
        V[][] newInnerV = (V[][]) Array.newInstance(valueClass, rowSize + 1, 0);
        System.arraycopy(innerV, 0, newInnerV, 0, putIndex);
        //需要移位,表示高位有数据要迁移,否则就不需要迁移(强行迁移反而会出错)
        if(needMove > 0) {
            System.arraycopy(innerV, putIndex, newInnerV, putIndex + 1, needMove);

            //位置重新计算,上面的位置全部移到后面+列长度的位置上
            int startIdx = putIndex * columnSize;
            _resetRowBits(startIdx, columnSize, true);
        }
        innerV = newInnerV;

        //新数据放入
        rowIndex.put(rowKey, putIndex);
        innerV[putIndex] = (V[]) Array.newInstance(valueClass, 0);

        return putIndex;
    }

    /** 减少行, 迁移相应的下标位 */
    @SuppressWarnings("unchecked")
    private void _decRow(R rowKey, int rowIdx) {
        int rowSize = rowIndex.size();
        int columnSize = columnIndex.size();

        NavigableMap<R, Integer> tailMap = rowIndex.tailMap(rowKey, false);
        //下标移位
        tailMap.entrySet().forEach(t -> t.setValue(t.getValue() - 1));
        rowIndex.remove(rowKey);

        //数据移位
        int newRowSize = rowSize - 1;
        V[][] newInnerV = (V[][]) Array.newInstance(valueClass, newRowSize, 0);
        System.arraycopy(innerV, 0, newInnerV, 0, rowIdx);
        int needMove = newRowSize - rowIdx;
        System.arraycopy(innerV, rowIdx + 1, newInnerV, rowIdx, needMove);
        innerV = newInnerV;

        //标记位重建
        int startIdx = (rowIdx + 1) * columnSize;
        _resetRowBits(startIdx, columnSize, false);
    }

    /** 将指定的数组增长至指定位数 */
    private V[] _incArray(V[] vv, int size) {
        @SuppressWarnings("unchecked")
        V[] newVv = (V[]) Array.newInstance(valueClass, size);
        System.arraycopy(vv, 0, newVv, 0, vv.length);
        return newVv;
    }

    /** 增长列,同时迁移相应的下标位 */
    @SuppressWarnings("unchecked")
    private Integer _incColumn(C columnKey) {
        int columnSize = columnIndex.size();

        NavigableMap<C, Integer> tailMap = columnIndex.tailMap(columnKey, false);
        Integer putIndex = tailMap.isEmpty() ? columnSize : tailMap.firstEntry().getValue();

        //后面相应的下标项+1,之前的数据进行移位,然后新放入的数据相应的下标就是最前一位的下标

        //下标移位
        tailMap.entrySet().forEach(t -> t.setValue(t.getValue() + 1));

        //数据移位
        int needMove = columnSize - putIndex;
        if(needMove > 0) {
            for(int i = 0; i < innerV.length; i++) {
                V[] vv = innerV[i];
                int currentMove = vv.length - putIndex;
                //当前存储的列还不够应该放的长度,即表示不需要迁移,因为之前的数据都比要放的位置小
                if(currentMove < 0)
                    continue;

                @SuppressWarnings("unchecked")
                V[] newVv = (V[]) Array.newInstance(valueClass, vv.length + 1);

                System.arraycopy(vv, 0, newVv, 0, putIndex);
                System.arraycopy(vv, putIndex, newVv, putIndex + 1, currentMove);
                innerV[i] = newVv;
            }
        }

        //重建索引位
        _resetColumnBits(putIndex, putIndex, columnSize, true);

        //新数据放入
        columnIndex.put(columnKey, putIndex);

        return putIndex;
    }

    /** 减少列,迁移下标位 */
    private void _decColumn(C columnKey, int columnIdx) {
        int columnSize = columnIndex.size();
        NavigableMap<C, Integer> tailMap = columnIndex.tailMap(columnKey, false);
        //下标移位
        tailMap.entrySet().forEach(t -> t.setValue(t.getValue() - 1));
        columnIndex.remove(columnKey);

        //数据移位
        for(int i = 0; i < innerV.length; i++) {
            V[] vv = innerV[i];
            int newColumnSize = vv.length - 1;
            int currentMove = newColumnSize - columnIdx;
            //当前存储的列还不够应该放的长度,即表示不需要迁移,因为之前的数据都比要放的位置小
            if(currentMove < 0)
                continue;

            @SuppressWarnings("unchecked")
            V[] newVv = (V[]) Array.newInstance(valueClass, newColumnSize);
            System.arraycopy(vv, 0, newVv, 0, columnIdx);
            System.arraycopy(vv, columnIdx + 1, newVv, columnIdx, currentMove);
            innerV[i] = newVv;
        }

        //重建索引位
        _resetColumnBits(columnIdx, columnIdx, columnSize, false);
    }

    /** 移除单元格 */
    private V _remove(TableCell cell) {
        IdxV idxV = cell.idxV;
        return _remove(cell.rowKey, idxV.rowIdx, cell.columnKey, idxV.columnIdx, true, true);
    }

    /** 移除单元格,并检查是否要减行,或减列 */
    private V _remove(R rowKey, int rowIdx, C columnKey, int columnIdx, boolean checkDecRow, boolean checkDecColumn) {
        V[] rows = innerV[rowIdx];

        //超过长度位,即当前行没数据
        if(rows.length <= columnIdx)
            return null;

        val old = rows[columnIdx];
        rows[columnIdx] = null;

        //清除存在位
        _clearExist(rowIdx, columnIdx);

        //处理减行
        if(checkDecRow && _isRowEmpty(rowIdx))
            _decRow(rowKey, rowIdx);

        //处理减列
        if(checkDecColumn && _isColumnEmpty(columnIdx))
            _decColumn(columnKey, columnIdx);

        return old;
    }

    /** 移除行 */
    private void _remove(Row row) {
        R rowKey = row.rowKey;
        int rowIdx = rowIndex.get(rowKey);
        for(C columnKey : row.keySet()) {
            int columnIdx = columnIndex.get(columnKey);
            _remove(rowKey, rowIdx, columnKey, columnIdx, false, true);
        }

        //删减整行
        _decRow(rowKey, rowIdx);
    }

    /** 移除列 */
    private void _remove(Column column) {
        C columnKey = column.columnKey;
        int columnIdx = columnIndex.get(columnKey);
        for(R rowKey : column.keySet()) {
            int rowIdx = rowIndex.get(rowKey);
            _remove(rowKey, rowIdx, columnKey, columnIdx, true, false);
        }

        //删除整列
        _decColumn(columnKey, columnIdx);
    }

    /** 指定行是否已经空了(没有存放数据) */
    private boolean _isRowEmpty(int rowIdx) {
        V[] vv = innerV[rowIdx];
        for(V v : vv) {
            if(v != null)
                return false;
        }

        return true;
    }

    /** 指定列是否已经空了(没有存放数据) */
    private boolean _isColumnEmpty(int columnIdx) {
        for(V[] vv : innerV) {
            if(vv.length > columnIdx && vv[columnIdx] != null)
                return false;
        }

        return true;
    }

    /** 变更行设置标记位 */
    private void _resetRowBits(int startIdx, int columnSize, boolean inc) {
        //如果是比如删除最后一行,则相应的startIdx会比当前长度更长,这里就直接返回,不再处理
        if(startIdx > bitsSet.length())
            return;

        //不是增长,则相应的步进往后跨一列长
        if(!inc)
            columnSize = -columnSize;

        //clone一份,以进行判定,避免单对象上处理会出问题
        BitSet cloned = (BitSet) bitsSet.clone();
        bitsSet.clear(startIdx, bitsSet.length());//先清除相应的数据
        for(int i = cloned.nextSetBit(startIdx); i >= 0; i = cloned.nextSetBit(i + 1)) {
            bitsSet.set(i + columnSize);
        }
        bits = BitSetUtils.getLongArray(bitsSet);
    }

    /** 变更列设置标记位 */
    private void _resetColumnBits(int startIdx, int columnIdx, int columnSize, boolean inc) {
        int afterColumnIdxStep = inc ? 1 : -1;

        //clone一份,以进行判定,避免单对象上处理会出问题
        BitSet cloned = (BitSet) bitsSet.clone();
        bitsSet.clear(startIdx, bitsSet.length());//先清除相应的数据
        for(int i = cloned.nextSetBit(startIdx); i >= 0; i = cloned.nextSetBit(i + 1)) {
            int changeSize = i / columnSize;
            if(!inc)
                changeSize = -changeSize;

            int newIdx = i + changeSize;
            if(i % columnSize >= columnIdx)
                newIdx += afterColumnIdxStep;

            bitsSet.set(newIdx);
        }
        bits = BitSetUtils.getLongArray(bitsSet);
    }

    /** 设置存在位 */
    private void _setExist(int rowIdx, int columnIdx) {
        bitsSet.set(rowIdx * columnIndex.size() + columnIdx);
        bits = BitSetUtils.getLongArray(bitsSet);
    }

    /** 清除存在位 */
    private void _clearExist(int rowIdx, int columnIdx) {
        bitsSet.clear(rowIdx * columnIndex.size() + columnIdx);
        bits = BitSetUtils.getLongArray(bitsSet);
    }

    /** 通过绝对下标位定义元素,同时返回相应的行,列坐标 */
    private IdxV _idxV(int index) {
        int columnSize = columnIndex.size();
        int rowIdx = index / columnSize;
        int columnIdx = index % columnSize;
        return new IdxV(rowIdx, columnIdx, innerV[rowIdx][columnIdx]);
    }

    /** 根据行数获取行值 */
    @SuppressWarnings("unchecked")
    private R _rKey(int rowIdx) {
        return rowIndex.keySet().toArray((R[]) Array.newInstance(rowClass, rowIndex.size()))[rowIdx];
    }

    /** 根据列数获取列值 */
    @SuppressWarnings("unchecked")
    private C _cKey(int columnIdx) {
        return columnIndex.keySet().toArray((C[]) Array.newInstance(columnClass, columnIndex.size()))[columnIdx];
    }

    /** 找到单元格 */
    private TableCell _cell(V _v) {
        if(innerV == null)
            return null;

        int columnSize = columnIndex.size();

        for(int i = bitsSet.nextSetBit(0); i >= 0; i = bitsSet.nextSetBit(i + 1)) {
            int rowIdx = i / columnSize;
            int columnIdx = i % columnSize;
            V v = innerV[rowIdx][columnIdx];
            if(Objects.equals(v, _v)) {
                return new TableCell(new IdxV(rowIdx, columnIdx, v));
            }
        }

        return null;
    }

    /** 找到行 */
    private Row _row(R rowKey) {
        if(!containsRow(rowKey))
            return null;

        return new Row(rowKey);
    }

    /** 找到列 */
    private Column _column(C columnKey) {
        if(!containsColumn(columnKey))
            return null;

        return new Column(columnKey);
    }

    //---------------------------- 内部各种方法 end ------------------------------//

    /** 移除整行 */
    public Map<C, V> removeRow(Object rowKey) {
        Row row = _row(_r(rowKey));
        if(row != null)
            _remove(row);

        return row;
    }

    /** 称除整列 */
    public Map<R, V> removeColumn(Object columnKey) {
        Column column = _column(_c(columnKey));
        if(column != null)
            _remove(column);

        return column;
    }

    /** 移除特定格 */
    public V removeValue(Object value) {
        TableCell cell = _cell(_v(value));

        return cell == null ? null : _remove(cell);
    }

    /** 找到相应的单元格 */
    public Cell<R, C, V> cell(Object value) {
        return _cell(_v(value));
    }

    @Override
    public boolean contains(@NonNull @Nullable Object rowKey, @NonNull @Nullable Object columnKey) {
        return containsRow(rowKey) && containsColumn(columnKey);
    }

    @Override
    public boolean containsRow(@NonNull @Nullable Object rowKey) {
        return rowIndex.containsKey(_r(rowKey));
    }

    @Override
    public boolean containsColumn(@NonNull @Nullable Object columnKey) {
        return columnIndex.containsKey(_c(columnKey));
    }

    @Override
    public boolean containsValue(@NonNull @Nullable Object value) {
        return cell(value) != null;
    }

    @Override
    public V get(@Nullable Object rowKey, @Nullable Object columnKey) {
        val rowIdx = rowIndex.get(_r(rowKey));
        if(rowIdx == null)
            return null;

        val columnIdx = columnIndex.get(_c(columnKey));
        if(columnIdx == null)
            return null;

        V[] rows = innerV[rowIdx];
        return rows.length > columnIdx ? rows[columnIdx] : null;
    }

    @Override
    public boolean isEmpty() {
        return rowIndex.isEmpty();
    }

    @Override
    public int size() {
        if(isEmpty())
            return 0;

        return bitsSet.cardinality();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void clear() {
        rowIndex.clear();
        columnIndex.clear();
        innerV = (V[][]) Array.newInstance(valueClass, 0, 0);
        bitsSet.clear();
    }

    @Override
    public V put(@Nonnull R rowKey, @Nonnull C columnKey, @Nonnull V value) {
        Integer rowIdx = rowIndex.get(rowKey);
        if(rowIdx == null)
            rowIdx = _incRow(rowKey);

        Integer columnIdx = columnIndex.get(columnKey);
        if(columnIdx == null)
            columnIdx = _incColumn(columnKey);

        V[] vv = innerV[rowIdx];//这里肯定有值,因此前面已经增长过了
        if(vv.length <= columnIdx) {
            innerV[rowIdx] = vv = _incArray(vv, columnIdx + 1);
        }

        V old = vv[columnIdx];
        vv[columnIdx] = value;

        //标记位处理
        _setExist(rowIdx, columnIdx);

        return old;
    }

    /** 插入一个表格,不支持null */
    @Override
    public void putAll(@Nonnull Table<? extends R, ? extends C, ? extends V> table) {
        for(Cell<? extends R, ? extends C, ? extends V> cell : table.cellSet()) {
            R rowKey = cell.getRowKey();
            C columnKey = cell.getColumnKey();
            V value = cell.getValue();
            if(rowKey == null || columnKey == null || value == null)
                continue;

            put(rowKey, columnKey, value);
        }
    }

    @Override
    public V remove(@Nullable Object rowKey, @Nullable Object columnKey) {
        R _rKey = _r(rowKey);
        val rowIdx = rowIndex.get(_rKey);
        if(rowIdx == null)
            return null;

        C _cKey = _c(columnKey);
        val columnIdx = columnIndex.get(_cKey);
        if(columnIdx == null)
            return null;

        return _remove(_rKey, rowIdx, _cKey, columnIdx, true, true);
    }

    @Override
    public Map<C, V> row(@Nonnull R rowKey) {
        return _row(rowKey);
    }

    @Override
    public Map<R, V> column(@Nonnull C columnKey) {
        if(!containsColumn(columnKey))
            return null;

        return new Column(columnKey);
    }

    @Override
    public Set<Cell<R, C, V>> cellSet() {
        if(cellSet == null)
            cellSet = new CellSet();

        return cellSet;
    }

    @Override
    public Set<R> rowKeySet() {
        return rowMap().keySet();
    }

    @Override
    public Set<C> columnKeySet() {
        return columnMap().keySet();
    }

    @Override
    public Collection<V> values() {
        if(valueCollection == null)
            valueCollection = new ValueCollection();

        return valueCollection;
    }

    @Override
    public Map<R, Map<C, V>> rowMap() {
        if(rowMap == null)
            rowMap = new RowMap();

        return rowMap;
    }

    @Override
    public Map<C, Map<R, V>> columnMap() {
        if(columnMap == null)
            columnMap = new ColumnMap();

        return columnMap;
    }

    @RequiredArgsConstructor
    private class Row extends AbstractMap<C, V> {
        private final R rowKey;

        private transient RowEntrySet entrySet;

        /** 相应在索引中具体有效的起始位置 */
        private int _bitStart() {
            return rowIndex.get(rowKey) * columnIndex.size();
        }

        /** 在索引中有效的结束位置 */
        private int _bitEnd() {
            return _bitStart() + columnIndex.size();
        }

        @Override
        public V get(Object key) {
            return ArrayTreeTable.this.get(rowKey, key);
        }

        @Override
        public V put(C key, V value) {
            return ArrayTreeTable.this.put(rowKey, key, value);
        }

        @Override
        public V remove(Object key) {
            return ArrayTreeTable.this.remove(rowKey, key);
        }

        @Override
        @Nonnull
        public Set<Entry<C, V>> entrySet() {
            if(entrySet == null)
                entrySet = new RowEntrySet();

            return entrySet;
        }

        @Override
        public boolean isEmpty() {
            return !ArrayTreeTable.this.containsRow(rowKey);
        }

        @Override
        public boolean containsKey(Object key) {
            return ArrayTreeTable.this.contains(rowKey, key);
        }

        @Override
        public void clear() {
            //避免重复删除以及内部逻辑出错
            if(!isEmpty())
                ArrayTreeTable.this._remove(this);
        }

        private class RowEntrySet extends AbstractSet<Map.Entry<C, V>> {
            @Override
            public boolean remove(Object o) {
                V v = Row.this.remove(o);
                return v != null;
            }

            @Override
            public int size() {
                if(isEmpty())
                    return 0;

                //位计算
                int start = _bitStart();
                int end = _bitEnd();
                int count = 0;
                for(int k = bitsSet.nextSetBit(start); k >= 0 && k < end; k = bitsSet.nextSetBit(k + 1))
                    count++;

                return count;
            }

            @Override
            public boolean isEmpty() {
                return Row.this.isEmpty();
            }

            @Override
            public void clear() {
                Row.this.clear();
            }

            @Override
            @Nonnull
            public Iterator<Entry<C, V>> iterator() {
                if(isEmpty()) {
                    return Collections.emptyIterator();
                }

                return new Iterator<Entry<C, V>>() {
                    private int start = _bitStart();
                    private int end = _bitEnd();
                    /** 当前行中相应的列的下标位 */
                    private List<C> cList = new ArrayList<>(columnIndex.keySet());

                    private transient int index = start;

                    private transient Entry<C, V> next;

                    private int _nextValidIdx() {
                        int k = bitsSet.nextSetBit(index);
                        return k >= 0 && k < end ? k : -1;
                    }

                    @Override
                    public boolean hasNext() {
                        return _nextValidIdx() != -1;
                    }

                    @Override
                    public Entry<C, V> next() {
                        int idx = _nextValidIdx();
                        if(idx == -1)
                            throw new NoSuchElementException();

                        IdxV idxV = _idxV(idx);
                        next = new MapEntry<>(cList.get(idxV.columnIdx), idxV);
                        index = idx + 1;//索引往后移,以保证不会重新拿到当前对象

                        return next;
                    }

                    @Override
                    public void remove() {
                        if(next == null) {
                            throw new RuntimeException("不正确的移除,请先调用next");
                        }

                        Row.this.remove(next.getKey());
                        next = null;//保证不会重复移除
                    }
                };
            }
        }
    }

    private class RowMap extends AbstractMap<R, Map<C, V>> {
        private transient RowMapEntrySet entrySet;

        @Override
        public boolean containsKey(Object key) {
            return ArrayTreeTable.this.containsRow(key);
        }

        @Override
        public Map<C, V> get(Object key) {
            return ArrayTreeTable.this.row(_r(key));
        }

        @Override
        public Map<C, V> put(R key, Map<C, V> value) {
            throw new UnsupportedOperationException("基于行的RowMap不允许直接添加(或替换)一整行");
        }

        @Override
        public Map<C, V> remove(Object key) {
            return ArrayTreeTable.this.removeRow(key);
        }

        @Override
        public int size() {
            return rowIndex.size();
        }

        @Override
        @Nonnull
        public Set<Entry<R, Map<C, V>>> entrySet() {
            if(entrySet == null)
                entrySet = new RowMapEntrySet();

            return entrySet;
        }

        private class RowMapEntrySet extends AbstractSet<Entry<R, Map<C, V>>> {
            @Override
            @Nonnull
            public Iterator<Entry<R, Map<C, V>>> iterator() {
                if(isEmpty())
                    return Collections.emptyIterator();

                return new Iterator<Entry<R, Map<C, V>>>() {
                    private Iterator<R> rIterator = rowIndex.keySet().iterator();
                    private transient Entry<R, Map<C, V>> next;

                    @Override
                    public boolean hasNext() {
                        return rIterator.hasNext();
                    }

                    @Override
                    public Entry<R, Map<C, V>> next() {
                        R rowKey = rIterator.next();
                        Map<C, V> row = RowMap.this.get(rowKey);
                        next = new MapMapEntry<>(rowKey, row);
                        return next;
                    }

                    @Override
                    public void remove() {
                        if(next == null) {
                            throw new RuntimeException("不正确的移除,请先调用next");
                        }

                        RowMap.this.remove(next.getKey());
                        next = null;//保证不会重复移除
                    }
                };
            }

            @Override
            public int size() {
                return RowMap.this.size();
            }
        }
    }

    @RequiredArgsConstructor
    private class Column extends AbstractMap<R, V> {
        private final C columnKey;

        private transient ColumnEntrySet entrySet;

        @Override
        public V get(Object key) {
            return ArrayTreeTable.this.get(key, columnKey);
        }

        @Override
        public V put(R key, V value) {
            return ArrayTreeTable.this.put(key, columnKey, value);
        }

        @Override
        public V remove(Object key) {
            return ArrayTreeTable.this.remove(key, columnKey);
        }

        @Override
        @Nonnull
        public Set<Entry<R, V>> entrySet() {
            if(entrySet == null)
                entrySet = new ColumnEntrySet();

            return entrySet;
        }

        @Override
        public boolean isEmpty() {
            return !ArrayTreeTable.this.containsColumn(columnKey);
        }

        @Override
        public boolean containsKey(Object key) {
            return ArrayTreeTable.this.contains(key, columnKey);
        }

        @Override
        public void clear() {
            //避免重复处理及内部逻辑出错
            if(isEmpty())
                ArrayTreeTable.this._remove(this);
        }

        private class ColumnEntrySet extends AbstractSet<Map.Entry<R, V>> {
            @Override
            public boolean remove(Object o) {
                V v = Column.this.remove(o);
                return v != null;
            }

            @Override
            public int size() {
                if(isEmpty())
                    return 0;

                //这里直接采用数组计算,以快速定位到指定位置
                int columnIdx = columnIndex.get(columnKey);
                int rowSize = rowIndex.size();
                int cnt = 0;
                for(int rowIdx = 0; rowIdx < rowSize; rowIdx++) {
                    V[] vv = innerV[rowIdx];
                    if(vv.length > columnIdx && vv[columnIdx] != null)
                        cnt++;
                }

                return cnt;
            }

            @Override
            public boolean isEmpty() {
                return Column.this.isEmpty();
            }

            @Override
            public void clear() {
                Column.this.clear();
            }

            @Override
            @Nonnull
            public Iterator<Entry<R, V>> iterator() {
                if(isEmpty()) {
                    return Collections.emptyIterator();
                }

                return new Iterator<Entry<R, V>>() {
                    private int rowIdx = 0;
                    private int rowSize = rowIndex.size();
                    private int columnIdx = columnIndex.get(columnKey);

                    private transient List<R> rList = new ArrayList<>(rowIndex.keySet());
                    private transient int foundRowIdx = -1;
                    private transient Entry<R, V> next;

                    private boolean _toNextValid() {
                        while(rowIdx < rowSize) {
                            V[] vv = innerV[rowIdx];
                            if(vv.length > columnIdx && vv[columnIdx] != null) {
                                foundRowIdx = rowIdx;
                                return true;
                            }
                            rowIdx++;
                        }

                        return false;
                    }

                    @SuppressWarnings("SimplifiableIfStatement")
                    @Override
                    public boolean hasNext() {
                        //之前已经检测过,则直接返回
                        if(foundRowIdx != -1)
                            return true;

                        return _toNextValid();
                    }

                    @Override
                    public Entry<R, V> next() {
                        if(foundRowIdx == -1 && !_toNextValid())
                            throw new NoSuchElementException();

                        V v = innerV[foundRowIdx][columnIdx];
                        next = new MapEntry<>(rList.get(foundRowIdx), new IdxV(foundRowIdx, columnIdx, v));
                        foundRowIdx = -1;//重置标记位
                        rowIdx++;//推到下一行

                        return next;
                    }

                    @Override
                    public void remove() {
                        if(next == null) {
                            throw new RuntimeException("不正确的移除,请先调用next");
                        }

                        Column.this.remove(next.getKey());
                        next = null;//保证不会重复移除
                    }
                };
            }
        }
    }

    private class ColumnMap extends AbstractMap<C, Map<R, V>> {
        private transient ColumnMapEntrySet entrySet;

        @Override
        public boolean containsKey(Object key) {
            return ArrayTreeTable.this.containsColumn(key);
        }

        @Override
        public Map<R, V> get(Object key) {
            return ArrayTreeTable.this.column(_c(key));
        }

        @Override
        public Map<R, V> put(C key, Map<R, V> value) {
            throw new UnsupportedOperationException("基于列的ColumnMap不允许直接添加(或替换)一整列");
        }

        @Override
        public Map<R, V> remove(Object key) {
            return ArrayTreeTable.this.removeColumn(key);
        }

        @Override
        public int size() {
            return columnIndex.size();
        }

        @Override
        @Nonnull
        public Set<Entry<C, Map<R, V>>> entrySet() {
            if(entrySet == null)
                entrySet = new ColumnMapEntrySet();

            return entrySet;
        }

        private class ColumnMapEntrySet extends AbstractSet<Entry<C, Map<R, V>>> {
            @Override
            @Nonnull
            public Iterator<Entry<C, Map<R, V>>> iterator() {
                if(isEmpty())
                    return Collections.emptyIterator();

                return new Iterator<Entry<C, Map<R, V>>>() {
                    private Iterator<C> cIterator = columnIndex.keySet().iterator();
                    private transient Entry<C, Map<R, V>> next;

                    @Override
                    public boolean hasNext() {
                        return cIterator.hasNext();
                    }

                    @Override
                    public Entry<C, Map<R, V>> next() {
                        C columnKey = cIterator.next();
                        Map<R, V> column = ColumnMap.this.get(columnKey);
                        next = new MapMapEntry<>(columnKey, column);
                        return next;
                    }

                    @Override
                    public void remove() {
                        if(next == null) {
                            throw new RuntimeException("不正确的移除,请先调用next");
                        }

                        ColumnMap.this.remove(next.getKey());
                        next = null;//保证不会重复移除
                    }
                };
            }

            @Override
            public int size() {
                return ColumnMap.this.size();
            }
        }
    }

    private class CellSet extends AbstractSet<Cell<R, C, V>> {
        @Override
        @Nonnull
        public Iterator<Cell<R, C, V>> iterator() {
            return new Iterator<Cell<R, C, V>>() {
                private List<R> rList = new ArrayList<>(rowIndex.keySet());
                private List<C> cList = new ArrayList<>(columnIndex.keySet());
                private int index = 0;

                private transient TableCell next;

                private int _nextValidIndex() {
                    return bitsSet.nextSetBit(index);
                }

                @Override
                public boolean hasNext() {
                    return _nextValidIndex() != -1;
                }

                @Override
                public Cell<R, C, V> next() {
                    int idx = _nextValidIndex();
                    if(idx == -1)
                        throw new NoSuchElementException();

                    IdxV idxV = _idxV(idx);
                    next = new TableCell(rList.get(idxV.rowIdx), cList.get(idxV.columnIdx), idxV);
                    index++;//索引往后走,避免反复处理当前格

                    return next;
                }

                @Override
                public void remove() {
                    if(next == null)
                        throw new RuntimeException("必须先调用next");

                    ArrayTreeTable.this.remove(next.rowKey, next.columnKey);
                    next = null;
                }
            };
        }

        @Override
        public int size() {
            return ArrayTreeTable.this.size();
        }
    }

    private class ValueCollection extends AbstractCollection<V> {
        @Override
        @Nonnull
        public Iterator<V> iterator() {
            return new Iterator<V>() {
                private int index = 0;
                private List<R> rList = new ArrayList<>(rowIndex.keySet());
                private List<C> cList = new ArrayList<>(columnIndex.keySet());

                private transient TableCell next;

                private int _nextValidIndex() {
                    return bitsSet.nextSetBit(index);
                }

                @Override
                public boolean hasNext() {
                    return _nextValidIndex() != -1;
                }

                @Override
                public V next() {
                    int idx = _nextValidIndex();
                    if(idx == -1)
                        throw new NoSuchElementException();

                    IdxV idxV = _idxV(idx);
                    next = new TableCell(rList.get(idxV.rowIdx), cList.get(idxV.columnIdx), idxV);
                    index++;//增长查询下标,避免重复定位

                    return next.getValue();
                }

                @Override
                public void remove() {
                    if(next == null)
                        throw new RuntimeException("请先调用next");

                    ArrayTreeTable.this._remove(next);
                    next = null;
                }
            };
        }

        @Override
        public int size() {
            return ArrayTreeTable.this.size();
        }

        @Override
        public boolean contains(Object o) {
            return ArrayTreeTable.this.containsValue(o);
        }

        @Override
        public boolean remove(Object o) {
            return ArrayTreeTable.this.removeValue(o) != null;
        }

        @Override
        public void clear() {
            ArrayTreeTable.this.clear();
        }
    }

    private class MapEntry<X> implements Map.Entry<X, V> {
        /** 相应的key,可能是行,也可能是列 */
        @Getter
        private X key;
        /** 绝对定位下的值信息 */
        private IdxV idxV;

        MapEntry(X key, IdxV idxV) {
            this.key = key;
            this.idxV = idxV;
        }

        @Override
        public V getValue() {
            return idxV.v;
        }

        @Override
        public V setValue(V value) {
            return idxV.change(value);
        }
    }

    @RequiredArgsConstructor
    @ToString
    private class IdxV {
        /** 行下标 */
        private final int rowIdx;
        /** 列下标 */
        private final int columnIdx;
        /** 具体的值 */
        @NonNull
        private V v;

        /** 修改相应的值 */
        public V change(V newV) {
            V old = v;
            innerV[rowIdx][columnIdx] = this.v = newV;

            return old;
        }
    }

    @RequiredArgsConstructor
    @ToString
    private class TableCell implements Cell<R, C, V> {
        @Getter
        private final R rowKey;
        @Getter
        private final C columnKey;
        private final IdxV idxV;

        TableCell(IdxV idxV) {
            this(_rKey(idxV.rowIdx), _cKey(idxV.columnIdx), idxV);
        }

        @Override
        public V getValue() {
            return idxV.v;
        }
    }

    @RequiredArgsConstructor
    @Getter
    private class MapMapEntry<X, Y> implements Map.Entry<X, Map<Y, V>> {
        private final X key;
        private final Map<Y, V> value;

        @Override
        public Map<Y, V> setValue(Map<Y, V> value) {
            throw new UnsupportedOperationException("不允许直接改变一行(列)");
        }
    }
}