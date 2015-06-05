/*
 *    Copyright 2009-2013 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.ObjectTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * @author Iwao AVE!
 */
class ResultSetWrapper {
  //statment.execute()以后返回的resultset对象	
  private final ResultSet resultSet;
  //
  private final TypeHandlerRegistry typeHandlerRegistry;
  //resultset结果的所有列名或者是sql as集合
  private final List<String> columnNames = new ArrayList<String>();
  //resultset结果中的所有jdbctype对应的javatype
  private final List<String> classNames = new ArrayList<String>();
  //当前result中包含的jdbctype的顺序集合
  private final List<JdbcType> jdbcTypes = new ArrayList<JdbcType>();
  private final Map<String, Map<Class<?>, TypeHandler<?>>> typeHandlerMap = new HashMap<String, Map<Class<?>, TypeHandler<?>>>();
  private Map<String, List<String>> mappedColumnNamesMap = new HashMap<String, List<String>>();
  private Map<String, List<String>> unMappedColumnNamesMap = new HashMap<String, List<String>>();

  
  /**
   * 构造函数
   * @param rs
   * @param configuration
   * @throws SQLException
   */
  public ResultSetWrapper(ResultSet rs, Configuration configuration) throws SQLException {
    super();
    //java的8中基本类型
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    //初始化resultset
    this.resultSet = rs;
    //获取resultset的元数据
    final ResultSetMetaData metaData = rs.getMetaData();
    //获取表的所有列的数量
    final int columnCount = metaData.getColumnCount();
    //遍历每列
    for (int i = 1; i <= columnCount; i++) {
      //columnlabel和columnName的区别:getColumnName返回的是sql语句中field的原始名字。getColumnLabel是field的SQL AS的值
      columnNames.add(configuration.isUseColumnLabel() ? metaData.getColumnLabel(i) : metaData.getColumnName(i));
      //获取列类型的代码,例如  varchar -- 12 ,通过12查找对应的jdbc type
      jdbcTypes.add(JdbcType.forCode(metaData.getColumnType(i)));
      //getColumnClassName指的是数据库列对应的java类型,比如varchar -- string
      classNames.add(metaData.getColumnClassName(i));
    }
  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  /**
   * resultset结果的所有列名或者是sql as集合
   * @return
   */
  public List<String> getColumnNames() {
    return this.columnNames;
  }

  /**
   * Gets the type handler to use when reading the result set.
   * Tries to get from the TypeHandlerRegistry by searching for the property type.
   * If not found it gets the column JDBC type and tries to get a handler for it.
   * 
   * 翻译:当从resultset中取值的之前要获取属性成员的typehandler
   * 尝试从TypeHandlerRegistry(基本类型)获取,
   * 如果获取不到就获取类属性成员的columngname(列名)然偶尝试通过jdbctype获取typehandler
   * 
   * 
   * 总结:
   * 通过类属性成员的propertytype & columnname获取对应的typehandler,方便从resultset中取值
   * 
   * 
   * *通过缓存typeHandlerMap,以propertyType作为key尝试获取对应的typehandler,如果获取到就直接返回,
   * 
   * *如果获取不到,通过columnname获取javatype&jdbctype,然后从typeHandlerRegistry尝试获取,获取到就缓存并且返回
   * 
   * *如果获取不到或者是未知类型,就定义为ObjectTypeHandler返回,缓存并且返回
   * 
   * @param propertyType
   * @param columnName
   * @return
   */
  public TypeHandler<?> getTypeHandler(Class<?> propertyType, String columnName) {
    TypeHandler<?> handler = null;
    //从当前缓存的typeHandlerMap中获取columnname
    Map<Class<?>, TypeHandler<?>> columnHandlers = typeHandlerMap.get(columnName);
    
    if (columnHandlers == null) {
      columnHandlers = new HashMap<Class<?>, TypeHandler<?>>();
      typeHandlerMap.put(columnName, columnHandlers);
      
      //如果map中可以获取列名columnname的缓存,再通过属性类型获取typehandler
    } else {
      handler = columnHandlers.get(propertyType);
    }
    
    //如果获取不到缓存typehandler,尝试程序获取
    if (handler == null) {
      //尝试propertytype(类属性类型)是基本类型
      handler = typeHandlerRegistry.getTypeHandler(propertyType);
      // Replicate logic of UnknownTypeHandler#resolveTypeHandler
      // See issue #59 comment 10
      //如果handler不是基本类型,或者handler是未知的typehandler类型(可能是用户自定义类)
      if (handler == null || handler instanceof UnknownTypeHandler) {
    	//通过列名(columnname)获取jdbctype
        final int index = columnNames.indexOf(columnName);
        final JdbcType jdbcType = jdbcTypes.get(index);
        //classNames.get(index)获取列名对应的java类属性类型的全县定名
        //通过类型全限定名来获取类型的class类型返回javatype
        final Class<?> javaType = resolveClass(classNames.get(index));
        //如果javatype & jdbctype都不为空
        if (javaType != null && jdbcType != null) {
          //根据java类型和jdbc类型获取typehandler
          handler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
          
          //如果只是javatype不为空
        } else if (javaType != null) {
        	
          //只通过javatype获取TypeHandler
          handler = typeHandlerRegistry.getTypeHandler(javaType);
          
          //如果只是jdbctype不为空,那么只通过jdbctype来获取typehandler
        } else if (jdbcType != null) {
          handler = typeHandlerRegistry.getTypeHandler(jdbcType);
        }
      }
      
      //如果找不到对应的typehandler  或者  handler是未知的,那么定义handler为ObjectTypeHandler
      if (handler == null || handler instanceof UnknownTypeHandler) {
        handler = new ObjectTypeHandler();
      }
      //将  propertytype(javatype)  --  handler  缓存到typeHandlerMap里边
      columnHandlers.put(propertyType, handler);
    }
    //返回根据属性类型和列名获取到的typehandler
    return handler;
  }

  
  /**
   * 通过类型全限定名来获取类型的class类型返回
   * @param className		类属性成员的类型全新定名
   * @return
   */
  private Class<?> resolveClass(String className) {
    try {
      final Class<?> clazz = Resources.classForName(className);
      return clazz;
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  
  /**
   * 将当前对象resultsetwrapper包装的resultset包含的resultmap(resultmapping)的字段集合添加到mappedColumnNamesMap
   * 将不包含的添加到unmappedColumnNames
   * @param resultMap
   * @param columnPrefix
   * @throws SQLException
   */
  private void loadMappedAndUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
	//当前对象resultsetwrapper包装的resultset包含的resultmap里边resultmapping设置的数据库列名
    List<String> mappedColumnNames = new ArrayList<String>();
    //不包含的
    List<String> unmappedColumnNames = new ArrayList<String>();
    
    //如果upperColumnPrefix不为空,将upperColumnPrefix大写
    final String upperColumnPrefix = columnPrefix == null ? null : columnPrefix.toUpperCase(Locale.ENGLISH);
    //在columnNames的每个元素添加prefix
    final Set<String> mappedColumns = prependPrefixes(resultMap.getMappedColumns(), upperColumnPrefix);
    //遍历每个列名
    for (String columnName : columnNames) {
      //将列名大写
      final String upperColumnName = columnName.toUpperCase(Locale.ENGLISH);
      //判断当前resultsetwrapper对象封装的resultset对象是否包含upperColumnName
      if (mappedColumns.contains(upperColumnName)) {
    	  //如果包含,将大写的upperColumnName添加到mappedColumnNames集合里边
        mappedColumnNames.add(upperColumnName);
      } else {
    	  //如果不包含,将原生的columnname添加到unmappedColumnNames里边
        unmappedColumnNames.add(columnName);
      }
    }
    //获取key,组装resultmapId + columnprefix 返回
    //将mappedColumnNames集合添加到当前对象resultsetwrapper包装的resultset包含的resultmap里边resultmapping设置的数据库列名
    mappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), mappedColumnNames);
    
    unMappedColumnNamesMap.put(getMapKey(resultMap, columnPrefix), unmappedColumnNames);
  }

  
  /**
   * 通过(resultmapId:columnPrefix)获取当前resultset(数据库返回列) & resultmapping(类属性)  并集
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  public List<String> getMappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
	//组装resultmapId + columnprefix 返回,格式  resultmapId:columnPrefix
	//通过(resultmapId:columnPrefix)获取当前resultset(数据库返回列) & resultmapping(类属性)  并集
    List<String> mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    //如果没有重合的部分
    if (mappedColumnNames == null) {
    	//将当前对象resultsetwrapper包装的resultset包含的resultmap(resultmapping)的字段集合添加到mappedColumnNamesMap
        // 将不包含的添加到unmappedColumnNames
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      //重新获取当前resultset(数据库返回列) & resultmapping(类属性)  并集
      mappedColumnNames = mappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    return mappedColumnNames;
  }

  
  /**
   * 当前对象resultsetwrapper包装的resultset不包含的resultmap(resultmapping)的字段集合返回
   * 
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  public List<String> getUnmappedColumnNames(ResultMap resultMap, String columnPrefix) throws SQLException {
	//将resultmapId + columnprefix作为key,从unMappedColumnNamesMap获取没有匹配的列名
    List<String> unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    //如果没有缓存数据,尝试获取
    if (unMappedColumnNames == null) {
      //将当前对象resultsetwrapper包装的resultset包含的resultmap(resultmapping)的字段集合添加到mappedColumnNamesMap
      // 将不包含的添加到unmappedColumnNames
      loadMappedAndUnmappedColumnNames(resultMap, columnPrefix);
      //通过key获取unmappedColumnNames集合
      unMappedColumnNames = unMappedColumnNamesMap.get(getMapKey(resultMap, columnPrefix));
    }
    
    return unMappedColumnNames;
  }

  
  /**
   * 组装resultmapId + columnprefix 返回,格式  resultmapId:columnPrefix
   * @param resultMap
   * @param columnPrefix
   * @return
   */
  private String getMapKey(ResultMap resultMap, String columnPrefix) {
    return resultMap.getId() + ":" + columnPrefix;
  }

  
  /**
   * 在columnNames的每个元素添加prefix
   * @param columnNames
   * @param prefix
   * @return
   */
  private Set<String> prependPrefixes(Set<String> columnNames, String prefix) {
	  //如果 columnnames 集合为空 || prefix为空,返回原生columnNames
    if (columnNames == null || columnNames.isEmpty() || prefix == null || prefix.length() == 0) {
      return columnNames;
    }
    //将columnnames中的每个columnname+prefix,然后将集合返回
    final Set<String> prefixed = new HashSet<String>();
    for (String columnName : columnNames) {
      prefixed.add(prefix + columnName);
    }
    return prefixed;
  }
  
}
