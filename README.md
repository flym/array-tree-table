# array-tree-table
使用二维数组实现的并且可用于排序的表格(Table), 支持json的序列化和反序列化

参考Table定义:[Table源码](https://github.com/google/guava/blob/master/guava/src/com/google/common/collect/Table.java)

## 实现目标
1. 查找 按行,按列,快速定位(guava中的table实现不能满足此要求)
2. 快速迭代,按行,按列,支持稀疏迭代
3. 泛型支持(通用化)
4. 支持排序(guava中的TreeTable为不可变的)
5. 行,列动态增长,削减
6. 反序列化支持(guava中的table均不支持或没有序列化器支持)

## 基本使用
1.使用代码

        //创建起相应的类
        ArrayTreeTable<String, String, String> table = new ArrayTreeTable<>(String.class, String.class, String.class,
                        (Class) String.CASE_INSENSITIVE_ORDER.getClass(), (Class) String.CASE_INSENSITIVE_ORDER.getClass());
        table.init();//必要的初始化代码

        //put数据
        table.put("a","b","ab");
        table.put("a","a","aa");

        //获取数据
        String value = table.get("a","b");
        //其它可参考guava Table的注解

2.用于描述Table的5个类型信息
    行,列,值,行比较器,列比较器分别对应rowClass,columnClass,valueClass,rowComparatorClass,columnComparatorClass
    因此在进行构建时,推荐使用此5个参数的构建器,传递相应的信息
    原无参构建函数专门用于反序列化器使用,不建议业务中使用

3.初始化
    在创建好table时,必须调用相应的init进行必要信息的初始化,以初始化相应的数据信息
    之所以不自动调用的原因是与反序列化兼容

4.API兼容性
    整个实现与Table上的api完全兼容,能够做到按行,按列均能以相同的性能进行调用
    在cell迭代时采用bitSet进行快速递进,避免在稀疏场景下的迭代问题

5.依赖项可选
    在ArrayTreeTable上的类依赖于fastjson和jackson,但由于是annotation,因此编译为jar之后.
    相应的依赖jar是可选的(optional),由java的getAnnotation并不会由于类的缺失由报错,会直接忽略不可接受的注解

## 序列化
1.fastjson

序列化时使用默认的方式即可,不需要作特别处理
由于table内自带相应的类型信息,因此使用fastjson时不再需要writeClass.
同时由于table集合的特殊性,writeClass会增加json的大小,因此不建议开启writeClass(由于是默认开启,因此需要将其关闭)
反序列需要注册指定的反序列化器,由类ArrayTreeTableDeserializer提供
使用方式可以参考测试类FastJsonUtils

2.jackson

序列化时使用默认的方式即可,不需要作特别处理
反序列化由类ArrayTreeTableDeserializer(包名中有jackson的类,注意与fastjson隔离)
由于已在类上面设定相应的反序列化器,因此不需要手动注册,但是由于deserializer要初始化其它信息,需要引用objectMapper
因此需要提前进行相应信息的注册和处理
使用方式可以参考测试类JacksonUtils
