/*
 *    Copyright 2009-2014 the original author or authors.
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

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.ResultLoader;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultResultSetHandler implements ResultSetHandler {

  private static final Object NO_VALUE = new Object();

  private final Executor executor;
  private final Configuration configuration;
  private final MappedStatement mappedStatement;
  private final RowBounds rowBounds;
  private final ParameterHandler parameterHandler;
  private final ResultHandler resultHandler;
  private final BoundSql boundSql;
  private final TypeHandlerRegistry typeHandlerRegistry;
  //在构造函数里边初始化,value是configuraton.getObjectfactory(),默认为defaultobjectfactory
  private final ObjectFactory objectFactory;

  // nested resultmaps
  private final Map<CacheKey, Object> nestedResultObjects = new HashMap<CacheKey, Object>();
  //缓存resultobject(祖先,id="ancestor")到ancestorObjects
  private final Map<CacheKey, Object> ancestorObjects = new HashMap<CacheKey, Object>();
  //缓存resultmapId -- columnPrefix到ancestorColumnPrefix
  private final Map<String, String> ancestorColumnPrefix = new HashMap<String, String>();

  // multiple resultsets
  private final Map<String, ResultMapping> nextResultMaps = new HashMap<String, ResultMapping>();
  private final Map<CacheKey, List<PendingRelation>> pendingRelations = new HashMap<CacheKey, List<PendingRelation>>();
  
  private static class PendingRelation {
    public MetaObject metaObject;
    public ResultMapping propertyMapping;
  }
  
  public DefaultResultSetHandler(Executor executor, MappedStatement mappedStatement, ParameterHandler parameterHandler, ResultHandler resultHandler, BoundSql boundSql,
      RowBounds rowBounds) {
    this.executor = executor;
    this.configuration = mappedStatement.getConfiguration();
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;
    this.parameterHandler = parameterHandler;
    this.boundSql = boundSql;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    this.resultHandler = resultHandler;
  }

  //
  // HANDLE OUTPUT PARAMETER
  //

  public void handleOutputParameters(CallableStatement cs) throws SQLException {
    final Object parameterObject = parameterHandler.getParameterObject();
    final MetaObject metaParam = configuration.newMetaObject(parameterObject);
    final List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    for (int i = 0; i < parameterMappings.size(); i++) {
      final ParameterMapping parameterMapping = parameterMappings.get(i);
      if (parameterMapping.getMode() == ParameterMode.OUT || parameterMapping.getMode() == ParameterMode.INOUT) {
        if (ResultSet.class.equals(parameterMapping.getJavaType())) {
          handleRefCursorOutputParameter((ResultSet) cs.getObject(i + 1), parameterMapping, metaParam);
        } else {
          final TypeHandler<?> typeHandler = parameterMapping.getTypeHandler();
          metaParam.setValue(parameterMapping.getProperty(), typeHandler.getResult(cs, i + 1));
        }
      }
    }
  }

  private void handleRefCursorOutputParameter(ResultSet rs, ParameterMapping parameterMapping, MetaObject metaParam) throws SQLException {
    try {
      final String resultMapId = parameterMapping.getResultMapId();
      final ResultMap resultMap = configuration.getResultMap(resultMapId);
      final DefaultResultHandler resultHandler = new DefaultResultHandler(objectFactory);
      final ResultSetWrapper rsw = new ResultSetWrapper(rs, configuration);
      handleRowValues(rsw, resultMap, resultHandler, new RowBounds(), null);
      metaParam.setValue(parameterMapping.getProperty(), resultHandler.getResultList());
    } finally {
      closeResultSet(rs); // issue #228 (close resultsets)
    }
  }

  //
  // HANDLE RESULT SETS
  //

  public List<Object> handleResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling results").object(mappedStatement.getId());
    
    final List<Object> multipleResults = new ArrayList<Object>();

    int resultSetCount = 0;
    // 获取stmt的第一个resultset对象并且包装成resultsetwrapper返回,如果没有 返回null
    ResultSetWrapper rsw = getFirstResultSet(stmt);
    //获取mappedstatement里边的resultmap集合
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
    int resultMapCount = resultMaps.size();
    //校验有返回结果resultset的情况下resultmap集合的数量是否是0
    validateResultMapsCount(rsw, resultMapCount);
    //如果查询的返回结果不为空 & mappedstatement里边设置的resultmaps数量 > 查询数据库返回的resultset列数量,一直循环
    while (rsw != null && resultMapCount > resultSetCount) {
      //按照索引index,0 ,, 1,,来获取resultmap
      ResultMap resultMap = resultMaps.get(resultSetCount);
      
      handleResultSet(rsw, resultMap, multipleResults, null);
      rsw = getNextResultSet(stmt);
      cleanUpAfterHandlingResultSet();
      resultSetCount++;
    }

    String[] resultSets = mappedStatement.getResulSets();
    if (resultSets != null) {
      while (rsw != null && resultSetCount < resultSets.length) {
        ResultMapping parentMapping = nextResultMaps.get(resultSets[resultSetCount]);
        if (parentMapping != null) {
          String nestedResultMapId = parentMapping.getNestedResultMapId();
          ResultMap resultMap = configuration.getResultMap(nestedResultMapId);
          handleResultSet(rsw, resultMap, null, parentMapping);
        }
        rsw = getNextResultSet(stmt);
        cleanUpAfterHandlingResultSet();
        resultSetCount++;
      }
    }

    return collapseSingleResultList(multipleResults);
  }

  
  /**
   * 
   * **stmt.execute(sql)方法会返回两种形式的数据
   * 	如果sql是查询语句,会返回resultSet
   * 	如果sql是更新语句,会返回更新列int
   * 
   * 获取stmt的第一个resultset对象并且包装成resultsetwrapper返回,如果没有返回null
   * @param stmt
   * @return
   * @throws SQLException
   */
  private ResultSetWrapper getFirstResultSet(Statement stmt) throws SQLException {
    ResultSet rs = stmt.getResultSet();
    //如果没有获取到resultset对象
    while (rs == null) {
      // move forward to get the first resultset in case the driver
      // doesn't return the resultset as the first result (HSQLDB 2.1)
      /*移动到此 Statement 对象的下一个结果，如果其为 ResultSet 对象，则返回 true，并隐式关闭利用方法 getResultSet 获取的所有当前 ResultSet 对象。 
    	当以下表达式为 true 时没有更多结果： 
        // stmt is a Statement object
        ((stmt.getMoreResults() == false) && (stmt.getUpdateCount() == -1))
		*/
      //判断stmt.execute()的返回结果是否是resultset对象,如果是resultset对象,说明是查询的sql语句
      if (stmt.getMoreResults()) {
        rs = stmt.getResultSet();
      } else {
    	//判断返回结果在不是resultset的情况下,是否是int,如果是int类型,表示是ddl的sql语句
    	//如果返回-1,说明 ((stmt.getMoreResults() == false) && (stmt.getUpdateCount() == -1)) ,,表达式为 true 时没有更多结果
        if (stmt.getUpdateCount() == -1) {
          // no more results. Must be no resultset
          break;
        }
      }
    }
    //如果返回结果不为空,就包装resultset并且搜集resultset的元数据resultsetmetadata构建ResultSetWrapper返回
    return rs != null ? new ResultSetWrapper(rs, configuration) : null;
  }

  private ResultSetWrapper getNextResultSet(Statement stmt) throws SQLException {
    // Making this method tolerant of bad JDBC drivers
    try {
      if (stmt.getConnection().getMetaData().supportsMultipleResultSets()) {
        // Crazy Standard JDBC way of determining if there are more results
        if (!((!stmt.getMoreResults()) && (stmt.getUpdateCount() == -1))) {
          ResultSet rs = stmt.getResultSet();
          return rs != null ? new ResultSetWrapper(rs, configuration) : null;
        }
      }
    } catch (Exception e) {
      // Intentionally ignored.
    }
    return null;
  }

  private void closeResultSet(ResultSet rs) {
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    }
  }

  private void cleanUpAfterHandlingResultSet() {
    nestedResultObjects.clear();
    ancestorColumnPrefix.clear();
  }

  
  /**
   * 校验mappedstatement里边设置的resultMapCount和resultsetwrapper里边数据库返回的列数量是否合法
   * 主要是看在有返回结果resultset的情况下resultmap集合的数量是否是0
   * @param rsw
   * @param resultMapCount
   */
  private void validateResultMapsCount(ResultSetWrapper rsw, int resultMapCount) {
    if (rsw != null && resultMapCount < 1) {
      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
          + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
    }
  }

  
  /**
   * 
   * @param rsw
   * @param resultMap
   * @param multipleResults
   * @param parentMapping			可能是指的嵌套的mapping,  one ,many
   * @throws SQLException
   */
  private void handleResultSet(ResultSetWrapper rsw, ResultMap resultMap, List<Object> multipleResults, ResultMapping parentMapping) throws SQLException {
    try {
      //如果是i嵌套的mapping
      if (parentMapping != null) {
        handleRowValues(rsw, resultMap, null, RowBounds.DEFAULT, parentMapping);
      } else {
    	//如果没有resulthandler
    	//注解方式resulthandler的使用方法:
    	 // 	@select(select * from student where id=#{id})
    	  //   pulic void getStudents(@param(id)String id , Resulthandler handler)
        if (resultHandler == null) {
          //实例化DefaultResultHandler,并且使用objectFactory(defaultobjectfactory)的create方法初始化List
          DefaultResultHandler defaultResultHandler = new DefaultResultHandler(objectFactory);
          //
          handleRowValues(rsw, resultMap, defaultResultHandler, rowBounds, null);
          multipleResults.add(defaultResultHandler.getResultList());
        } else {
          handleRowValues(rsw, resultMap, resultHandler, rowBounds, null);
        }
      }
    } finally {
      closeResultSet(rsw.getResultSet()); // issue #228 (close resultsets)
    }
  }

  private List<Object> collapseSingleResultList(List<Object> multipleResults) {
    if (multipleResults.size() == 1) {
      @SuppressWarnings("unchecked")
      List<Object> returned = (List<Object>) multipleResults.get(0);
      return returned;
    }
    return multipleResults;
  }

  //
  // HANDLE ROWS FOR SIMPLE RESULTMAP
  //

  private void handleRowValues(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
	//判断resultmap里边是否有嵌套类型的resultmapping  
    if (resultMap.hasNestedResultMaps()) {
    	//确认是否没有rowbound(内存分页),如果有,抛出异常
      ensureNoRowBounds();
      //resulthandler的合法性校验
      checkResultHandler();
      
      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    } else {
      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    }
  }  

  
  /**
   * 确认是否没有rowbound(内存分页),如果有,抛出异常
   */
  private void ensureNoRowBounds() {
    if (configuration.isSafeRowBoundsEnabled() && rowBounds != null && (rowBounds.getLimit() < RowBounds.NO_ROW_LIMIT || rowBounds.getOffset() > RowBounds.NO_ROW_OFFSET)) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely constrained by RowBounds. "
          + "Use safeRowBoundsEnabled=false setting to bypass this check.");
    }
  }

  
  /**
   * TODO
   * 如果有resulthandler & isSafeResultHandlerEnabled & isResultOrdered  & isResultOrdered
   */
  protected void checkResultHandler() {
    if (resultHandler != null && configuration.isSafeResultHandlerEnabled() && !mappedStatement.isResultOrdered()) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely used with a custom ResultHandler. "
          + "Use safeResultHandlerEnabled=false setting to bypass this check " 
          + "or ensure your statement returns ordered data and set resultOrdered=true on it.");
    }
  } 

  private void handleRowValuesForSimpleResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping)
      throws SQLException {
    //构造函数,初始化DefaultResultContext(数据库查询结果上下文)
    DefaultResultContext resultContext = new DefaultResultContext();
    //根据RowBounds(内存分页)来将resultset跳转到第offset行,如果rowbounds是默认的NO_ROW_OFFSET,则不会定位到指定位置
    skipRows(rsw.getResultSet(), rowBounds);
    //判断是否还有更多的row要处理,如果有的话，就将resultsetwrapper里边的resultset定位到下一个
    while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
      //处理resultmap里边的Discriminator(switch)情况,将resultmap返回
      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
      //处理一行数据组装的对象返回
      Object rowValue = getRowValue(rsw, discriminatedResultMap);
      storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
    }
  }

  
  private void storeObject(ResultHandler resultHandler, DefaultResultContext resultContext, Object rowValue, ResultMapping parentMapping, ResultSet rs) throws SQLException {
	//如果父类的mapping不为空,就是说存在嵌套情况  
    if (parentMapping != null) {
      //给parent映射字段赋值rowvalue
      linkToParents(rs, parentMapping, rowValue);
    } else {
       
      callResultHandler(resultHandler, resultContext, rowValue);
    }
  }

  private void callResultHandler(ResultHandler resultHandler, DefaultResultContext resultContext, Object rowValue) {
    resultContext.nextResultObject(rowValue);
    resultHandler.handleResult(resultContext);
  }

  
  /**
   * 判断是否还有更多的row要处理
   * 
   * context.isStopped()是否停止,默认为false
   * context.getResultCount()默认为0
   * rowBounds.getLimit()默认为Integer.maxvalue
   * 
   * @param context
   * @param rowBounds
   * @return
   * @throws SQLException
   */
  private boolean shouldProcessMoreRows(ResultContext context, RowBounds rowBounds) throws SQLException {
	 
    return !context.isStopped() && context.getResultCount() < rowBounds.getLimit();
  }

  
  /**
   * 根据RowBounds(内存分页)来将resultset跳转到第offset行,如果rowbounds是默认的NO_ROW_OFFSET,则不会定位到指定位置
   * @param rs
   * @param rowBounds
   * @throws SQLException
   */
  private void skipRows(ResultSet rs, RowBounds rowBounds) throws SQLException {
	//ResultSet.TYPE_FORWARD_ONLY,结果集的游标只能向下滚动  , 只能使用resultSet.getNext()
    if (rs.getType() != ResultSet.TYPE_FORWARD_ONLY) {
      //如果offset不等于Intege.maxvalue
      if (rowBounds.getOffset() != RowBounds.NO_ROW_OFFSET) {
    	  //将resultset定位到resultset的第rowBounds.getOffset()行
        rs.absolute(rowBounds.getOffset());
      }
    } else {
      //说明resultsettype是TYPE_FORWARD_ONLY,结果集的游标只能向下滚动  , 只能使用resultSet.getNext()
     //所以使用遍历的方式rs.next()来定位行
      for (int i = 0; i < rowBounds.getOffset(); i++) {
        rs.next();
      }
    }
  }

  //
  // GET VALUE FROM ROW FOR SIMPLE RESULT MAP
  //
  /**
   * 处理一行数据组装的对象返回
   * @param rsw
   * @param resultMap
   * @return
   * @throws SQLException
   */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap) throws SQLException {
    //实例化一个ResultLoaderMap，会在EnhancedResultObjectProxyImpl内部类中初始化代理的时候用到
    final ResultLoaderMap lazyLoader = new ResultLoaderMap();
    //根据resultmap的resulttype来初始化返回对象，如果对象的属性有嵌套查询并且支持懒加载，就返回代理对象，否则返回原生结果对象
    Object resultObject = createResultObject(rsw, resultMap, lazyLoader, null);
    //如果结果对象不为空，并且不是基本类型
    if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
      
      final MetaObject metaObject = configuration.newMetaObject(resultObject);
      //name  ,  phone就会在constructorResultMappings里边
      //如果对象的构造函数的参数mapping>0，说明foundValues=true
      boolean foundValues = resultMap.getConstructorResultMappings().size() > 0;
      
      //configuration.getAutoMappingBehavior()默认值是PARTICAL，所以第二个参数是true,就是说默认是自动匹配的PARTICAL
      if (shouldApplyAutomaticMappings(resultMap, !AutoMappingBehavior.NONE.equals(configuration.getAutoMappingBehavior()))) {
    	//基本类型的属性被赋值
        foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, null) || foundValues;
      }
      //有嵌套查询类型的属性，如果启用懒加载，就初始化一个loadpair放到resultloader里边
      //如果没有启动懒加载，就将查询出来的value赋值到metaobject的属性里
      foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, null) || foundValues;
      foundValues = lazyLoader.size() > 0 || foundValues;
      resultObject = foundValues ? resultObject : null;
      return resultObject;
    }
    return resultObject;
  }

  private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean def) {
    return resultMap.getAutoMapping() != null ? resultMap.getAutoMapping() : def;
  }

  //
  // PROPERTY MAPPINGS
  //
  /**
   * 
   * 获取那些在xml文件或者@results中设置的匹配属性
   * 	缓存获取
   * 	懒加载（事件触发获取）
   * 	直接获取
   * 
   * 
   * 获取在xml文件或者@results中设置的匹配属性的映射值
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param lazyLoader
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private boolean applyPropertyMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, ResultLoaderMap lazyLoader, String columnPrefix)
      throws SQLException {
	//通过(resultmapId:columnPrefix)获取当前resultset(数据库返回列) & resultmapping(类属性)  并集
	 //被匹配的字段名，这是些在xml文件或者@results中设置的匹配字段
    final List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
    boolean foundValues = false;
    //id1,id2,  age就会在propertyResultMappings里边
    final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
    //遍历所有在xml文件或者@results中设置的匹配字段
    for (ResultMapping propertyMapping : propertyMappings) {
      //如果有prefix,将返回(prefix+columnName),否则返回columnname
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      
      if (propertyMapping.isCompositeResult() 
          || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) 
          || propertyMapping.getResultSet() != null) {
    	//获取属性对应的数据库value返回，可能会是懒加载属性&缓存，会返回value=NO_VALUE，然后将懒加载属性加到loadermap里边
        Object value = getPropertyMappingValue(rsw.getResultSet(), metaObject, propertyMapping, lazyLoader, columnPrefix);
        //获取属性名
        final String property = propertyMapping.getProperty(); // issue #541 make property optional
        //如果value不为空就将value赋值到property上
        if (value != NO_VALUE && property != null && (value != null || configuration.isCallSettersOnNulls())) { // issue #377, call setter on nulls
          //赋值
          if (value != null || !metaObject.getSetterType(property).isPrimitive()) {
            metaObject.setValue(property, value);
          }
          foundValues = true;
        }
      }
    }
    return foundValues;
  }

  
  /**
   * 
   * 如果属性是嵌套查询类型的
   * 	获取嵌套查询属性对应的value并且返回
   * TODO:
   * 
   * 获取属性对应的数据库value返回
   * @param rs
   * @param metaResultObject
   * @param propertyMapping
   * @param lazyLoader
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getPropertyMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
      throws SQLException {
	 //如果这个属性是带有嵌套查询的,获取嵌套查询属性对应的value并且返回
    if (propertyMapping.getNestedQueryId() != null) {
      return getNestedQueryMappingValue(rs, metaResultObject, propertyMapping, lazyLoader, columnPrefix);
    } else if (propertyMapping.getResultSet() != null) {//如果这个属性不是嵌套查询并且
      addPendingChildRelation(rs, metaResultObject, propertyMapping);
      return NO_VALUE;
    } else if (propertyMapping.getNestedResultMapId() != null) {
      // the user added a column attribute to a nested result map, ignore it
      return NO_VALUE;
    } else {
      final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      return typeHandler.getResult(rs, column);
    }
  }

  
  
  /**
   * 参数介绍：
   * ResultSetWrapper	- 可以从里边获取resultset，里边包含着数据库的查询结果
   * MetaObject				- 方法返回类型的实例
   * ResultMap				- ResultMapping的集合
   * 
   * 这个方法的主要作用是根据resultmapping从resultset中获取value，然后将value赋值到metaobject里边
   * 但是这个方法里边只有基本类型的属性会被赋值，如果是对象或者集合（嵌套查询），就可能会启动懒加载，
   * 不会一开始就执行嵌套查询的加载
   * 
   * 默认自动匹配的mapping
   * 
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private boolean applyAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    //没有被匹配的字段，就是那些没有在xml配置文件或者@results的字段，
	  //跟方法名一样的默认自动匹配的mapping
    final List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
    boolean foundValues = false;
    for (String columnName : unmappedColumnNames) {
      String propertyName = columnName;
      if (columnPrefix != null && columnPrefix.length() > 0) {
        // When columnPrefix is specified,
        // ignore columns without the prefix.
        if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
          propertyName = columnName.substring(columnPrefix.length());
        } else {
          continue;
        }
      }
      final String property = metaObject.findProperty(propertyName, configuration.isMapUnderscoreToCamelCase());
      if (property != null && metaObject.hasSetter(property)) {
        final Class<?> propertyType = metaObject.getSetterType(property);
        if (typeHandlerRegistry.hasTypeHandler(propertyType)) {
          final TypeHandler<?> typeHandler = rsw.getTypeHandler(propertyType, columnName);
          final Object value = typeHandler.getResult(rsw.getResultSet(), columnName);
          if (value != null || configuration.isCallSettersOnNulls()) { // issue #377, call setter on nulls
            if (value != null || !propertyType.isPrimitive()) {
              metaObject.setValue(property, value);
            }
            foundValues = true;
          }
        }
      }
    }
    return foundValues;
  }

  // MULTIPLE RESULT SETS
  /**
   * 构建parentkey，根据parentkey来尝试获取PendingRelation集合
   * 判断parent映射
   * 如果是集合类型
   *    将rowvalue作为元素添加到集合中
   * 如果不是集合
   *    将rowvalue赋值给parent映射
   *    
   * 总结：
   *    给parent映射字段赋值rowvalue
   *    
   * @param rs
   * @param parentMapping
   * @param rowValue
   * @throws SQLException
   */
  private void linkToParents(ResultSet rs, ResultMapping parentMapping, Object rowValue) throws SQLException {
     //构建唯一cachekey 
    CacheKey parentKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getForeignColumn());
    //尝试使用parentkey从pendingRelations获取PendingRelation集合
    List<PendingRelation> parents = pendingRelations.get(parentKey);
    //循环遍历PendingRelation
    for (PendingRelation parent : parents) {
      if (parent != null) {
    	 //使用字段映射从metaobject中判断并且获取collection属性值，就是判断父类映射是不是一个集合类型的
        final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(parent.propertyMapping, parent.metaObject);
        //如果resultset一行的值组成的对象rowvalue不为空
        if (rowValue != null) {
            //如果父类映射属性是集合类型,就将rowvalue作为子元素添加到集合里边
          if (collectionProperty != null) {
            final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
            targetMetaObject.add(rowValue);
          } else {//如果父类不是集合类型，就直接将rowvalue作为值赋值给parent属性
            parent.metaObject.setValue(parent.propertyMapping.getProperty(), rowValue);
          }
        }
      }
    }
  }

  
  /**
   * 使用字段映射从metaobject中判断并且获取collection属性值
   * 
   * *如果属性值为空
   * 	-获取字段映射的java类型,如果是collection子类,实例化该类型,并且赋值到metaobject中,返回实例化对象
   * 
   * *如果属性值不为空&属性值是collection子类
   *   -直接返回属性值
   *   
   * *其他情况
   * 	-返回null
   * 
   * @param resultMapping
   * @param metaObject
   * @return
   */
  private Object instantiateCollectionPropertyIfAppropriate(ResultMapping resultMapping, MetaObject metaObject) {
	//获取resultmapping里边的propertyname
    final String propertyName = resultMapping.getProperty();
    //使用MethodInvoker根据prop获取目标对象object的value
    Object propertyValue = metaObject.getValue(propertyName);
    //如果值为空
    if (propertyValue == null) {
      //获取字段映射resultmapping的java类型
    	
    	
      //获取resultmapping的java类型
      Class<?> type = resultMapping.getJavaType();
      //如果java类型为空,就用metaobject获取属性name的set方法参数类型
      if (type == null) {
        type = metaObject.getSetterType(propertyName);
      }
      
      try {
    	//如果字段的java type是collection类型的,就使用objectFactory实例化type为propertyvalue,并且将属性名,属性value  set到metaObject里边
        if (objectFactory.isCollection(type)) {
         //根据type实例化对象，参赛类型为null,参数value为null
          propertyValue = objectFactory.create(type);
          //对该对象的name字段赋值value
          metaObject.setValue(propertyName, propertyValue);
          //返回实例化的属性value
          return propertyValue;
        }
      } catch (Exception e) {
        throw new ExecutorException("Error instantiating collection property for result '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
      }
      
      
      //如果metaObject获取的属性值为collection类型,直接返回
    } else if (objectFactory.isCollection(propertyValue.getClass())) {
      return propertyValue;
    }
    
    //返回空
    return null;
  }

  private void addPendingChildRelation(ResultSet rs, MetaObject metaResultObject, ResultMapping parentMapping) throws SQLException {
    CacheKey cacheKey = createKeyForMultipleResults(rs, parentMapping, parentMapping.getColumn(), parentMapping.getColumn());
    PendingRelation deferLoad = new PendingRelation();
    deferLoad.metaObject = metaResultObject;
    deferLoad.propertyMapping = parentMapping;
    List<PendingRelation> relations = pendingRelations.get(cacheKey);
    // issue #255
    if (relations == null) {
      relations = new ArrayList<DefaultResultSetHandler.PendingRelation>();
      pendingRelations.put(cacheKey, relations);
    }
    relations.add(deferLoad);
    ResultMapping previous = nextResultMaps.get(parentMapping.getResultSet());
    if (previous == null) {
      nextResultMaps.put(parentMapping.getResultSet(), parentMapping);
    } else {
      if (!previous.equals(parentMapping)) {
        throw new ExecutorException("Two different properties are mapped to the same resultSet");
      }
    }
  }

  
  /**
   * 
   * cachekey的组成元素
   * resultMapping
   * names[i]
   * value[i]
   * 
   * 总结：
   * 构建唯一cachekey
   * 
   * @param rs
   * @param resultMapping
   * @param names
   * @param columns
   * @return
   * @throws SQLException
   */
  private CacheKey createKeyForMultipleResults(ResultSet rs, ResultMapping resultMapping, String names, String columns) throws SQLException {
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMapping);
    if (columns != null && names != null) {
      String[] columnsArray = columns.split(",");
      String[] namesArray = names.split(",");
      for (int i = 0 ; i < columnsArray.length ; i++) {
        Object value = rs.getString(columnsArray[i]);
        if (value != null) {
          cacheKey.update(namesArray[i]);
          cacheKey.update(value);
        }
      }
    }
    return cacheKey;
  }

  //
  // INSTANTIATION & CONSTRUCTOR MAPPING
  //
	/**
	 * 根据查询的结果类型，
	 * 如果resultMap.getType()返回对象类型是基本类型：
	 * 获取resultmap的第一个resultmapping，并且根据它从ResultSetWrapper获取数据库查询的value返回
	 * 
	 * 如果resultType不是基本类型，而且是一个构造函数有参数的对象
	 * 返回构造函数带有参数的结果对象,并且将构造函数的参数的参数类型和参数值放到了constructorArgTypes  & constructorArgs
	 * 
	 * 如果返回类型不是基本类型，也不是带有参数构造函数的对象
	 * 根据type实例化对象，参赛类型为null,参数value为null
	 * 
	 * 初始化一个对象，可能会初始化构造函数参数，然后再根据PropertyResultMappings来遍历：
	 * 判断 propertyMapping 是否支持懒加载，并且是带有嵌套查询的,就返回代理对象
	 * 如果 propertyMapping有符合条件的，就返回代理对象
	 * 如果没有，就返回原生对象
	 * 
	 * 总结：
	 * 根据resultmap的resulttype来初始化返回对象，如果对象的属性有嵌套查询并且支持懒加载，
	 * 就返回代理对象，否则返回原生结果对象
	 * 
	 * @param rsw
	 * @param resultMap
	 * @param lazyLoader
	 * @param columnPrefix
	 * @return
	 * @throws SQLException
	 */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, ResultLoaderMap lazyLoader, String columnPrefix) throws SQLException {
	  //构造函数的参数类型集合
    final List<Class<?>> constructorArgTypes = new ArrayList<Class<?>>();
    //构造函数的参数value集合
    final List<Object> constructorArgs = new ArrayList<Object>();
    
    //构造函数参数赋值
    //创建返回对象，并且初始化对象构造函数的参数的value
    // 并且将构造函数的参数的参数类型和参数值放到了constructorArgTypes  & constructorArgs
    final Object resultObject = createResultObject(rsw, resultMap, constructorArgTypes, constructorArgs, columnPrefix);
    
    //属性赋值
    //如果返回对象不为null && 不是基本类型 （就是说如果返回对象是通过有参的构造函数初始化的）
    if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
      //id1,id2,  age就会在propertyResultMappings里边
      final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
      //遍历所有的属性mapping
      for (ResultMapping propertyMapping : propertyMappings) {
        //如果支持懒加载，并且是带有嵌套查询的,就返回代理对象
        if (propertyMapping.getNestedQueryId() != null && propertyMapping.isLazy()) { // issue gcode #109 && issue #149
          //默认使用CglibProxyFactory，创建resultObject()对象的代理返回
          return configuration.getProxyFactory().createProxy(resultObject, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
        }
      }
    }
    
    //返回赋值后的原生结果对象，而不是代理对象
    return resultObject;
  }

  
  /**
   * 
   * 如果resultMap.getType()返回对象类型是基本类型：
   * 获取resultmap的第一个resultmapping，并且根据它从ResultSetWrapper获取数据库查询的value返回
   * 
   * 如果resultType不是基本类型，而且是一个构造函数有参数的对象
   * 返回构造函数带有参数的结果对象,并且将构造函数的参数的参数类型和参数值放到了constructorArgTypes  & constructorArgs
   * 
   * 如果返回类型不是基本类型，也不是带有参数构造函数的对象
   * 根据type实例化对象，参赛类型为null,参数value为null
   * 
   * 
   * 总结：
   * 创建返回对象，并且初始化对象构造函数的参数的value
   * 并且将构造函数的参数的参数类型和参数值放到了constructorArgTypes  & constructorArgs
   * 
   * @param rsw
   * @param resultMap
   * @param constructorArgTypes
   * @param constructorArgs
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object createResultObject(ResultSetWrapper rsw, ResultMap resultMap, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix)
      throws SQLException {
	/*
	 * 
	 * @select("select * from student where id=2")
	 * public Student selectStudent();
	 * 
	 * resultmap.getType的返回值就是student
	 */
    final Class<?> resultType = resultMap.getType();
    //name  ,  phone就会在constructorResultMappings里边
    final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings();
    //如果resulttype是typeHandlerRegistry里边注册的基本类型
    // 获取resultmap的第一个resultmapping，并且根据它从ResultSetWrapper获取数据库查询的value返回
    if (typeHandlerRegistry.hasTypeHandler(resultType)) {
      //获取resultmap的第一个resultmapping，并且根据它从ResultSetWrapper获取数据库查询的value返回
      return createPrimitiveResultObject(rsw, resultMap, columnPrefix);
      
      
      //如果resultType不是基本类型，而且是一个构造函数有参数的对象
    } else if (constructorMappings.size() > 0) {
      //返回构造函数带有参数的结果对象,并且将构造函数的参数的参数类型和参数值放到了constructorArgTypes  & constructorArgs
      return createParameterizedResultObject(rsw, resultType, constructorMappings, constructorArgTypes, constructorArgs, columnPrefix);
      
      //如果返回类型不是基本类型，也不是带有参数构造函数的对象，
      //根据type实例化对象，参赛类型为null,参数value为null
    } else {
      return objectFactory.create(resultType);
    }
  }

  
  /**
   * 遍历constructorMappings，尝试从ResultSetWrapper获取mapping的value
   * 如果至少有一个parameter可以获取到value， foundvalues = true,objectfactory使用构造函数来初始化对象返回
   * 如果没有获取到value，就返回null
   * 
   * 总结：
   * 返回构造函数带有参数的结果对象
   * 
   * @param rsw
   * @param resultType
   * @param constructorMappings
   * @param constructorArgTypes
   * @param constructorArgs
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object createParameterizedResultObject(ResultSetWrapper rsw, Class<?> resultType, List<ResultMapping> constructorMappings, List<Class<?>> constructorArgTypes,
      List<Object> constructorArgs, String columnPrefix) throws SQLException {
    boolean foundValues = false;
    //遍历所有的构造函数的resultmapping，并且尝试获取constructor的参数值，如果获取了一个以上，就用objectFactory初始化对象返回
    for (ResultMapping constructorMapping : constructorMappings) {
      //获取mapping的type和columnName
      final Class<?> parameterType = constructorMapping.getJavaType();
      final String column = constructorMapping.getColumn();
      final Object value;
      //TODO：嵌套查询情况
      if (constructorMapping.getNestedQueryId() != null) {
        value = getNestedQueryConstructorValue(rsw.getResultSet(), constructorMapping, columnPrefix);
        
        //TODO：嵌套resultmap情况
      } else if (constructorMapping.getNestedResultMapId() != null) {
        final ResultMap resultMap = configuration.getResultMap(constructorMapping.getNestedResultMapId());
        value = getRowValue(rsw, resultMap);
        
        //如果没有嵌套查询情况和嵌套的结果情况，就使用columnName从resultsetWrapper中获取value
      } else {
        final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
        value = typeHandler.getResult(rsw.getResultSet(), prependPrefix(column, columnPrefix));
      }
      //将构造函数的参数类型和参数值添加到集合里边
      constructorArgTypes.add(parameterType);
      constructorArgs.add(value);
      //判断所有的参数是否至少有一个是非空的
      foundValues = value != null || foundValues;
    }
    //如果至少有一个parameter可以获取值，就使用objectfactory使用构造函数来初始化对象，否则，返回null
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  
  /**
   * Primitive 原生的
   * 
   * 例如：
   * @select("select sname from student where id=2")
   * public String selectStudent();
   * 
   * 如果resultmap中的resultmapping>0：
   * 从resultmap中获取第一个resultmapping的columnName(可能有前缀),然后从resultsetwrapper里边通过columnName来获取value返回
   * 如果resultmap中的resultmapping=0：
   * 获取（resultset结果的所有列名或者是sql as集合）   的第一个列名,然后从resultsetwrapper里边通过columnName来获取value返回
   * 
   * 总结(只在返回类型是基本类型情况下)：
   * 获取resultmap的第一个resultmapping，并且根据它从ResultSetWrapper获取数据库查询的value返回
   * 
   * @param rsw
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object createPrimitiveResultObject(ResultSetWrapper rsw, ResultMap resultMap, String columnPrefix) throws SQLException {
	//获取要返回的对象类型resulttype
    final Class<?> resultType = resultMap.getType();
    final String columnName;
    //如果resultmap中包含的字段映射resultmapping集合>0
    if (resultMap.getResultMappings().size() > 0) {
      //获取resultmap中resultmappings集合的第一个resultmapping(字段映射)的columnName列名(可能有列前缀)
      final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
      final ResultMapping mapping = resultMappingList.get(0);
      //如果有prefix,将返回(prefix+columnName),否则返回columnname
      columnName = prependPrefix(mapping.getColumn(), columnPrefix);
      
      //如果resultmap里边没有设置字段映射,获取（resultset结果的所有列名或者是sql as集合）   的第一个列名
    } else {
      columnName = rsw.getColumnNames().get(0);
    }
    //通过类属性成员的propertytype & columnname获取对应的typehandler,方便从resultset中取值
    final TypeHandler<?> typeHandler = rsw.getTypeHandler(resultType, columnName);
    //从数据库查询结果resultset中获取columnName的value
    return typeHandler.getResult(rsw.getResultSet(), columnName);
  }

  //
  // NESTED QUERY
  //

  private Object getNestedQueryConstructorValue(ResultSet rs, ResultMapping constructorMapping, String columnPrefix) throws SQLException {
    final String nestedQueryId = constructorMapping.getNestedQueryId();
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, constructorMapping, nestedQueryParameterType, columnPrefix);
    Object value = null;
    if (nestedQueryParameterObject != null) {
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      final Class<?> targetType = constructorMapping.getJavaType();
      final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
      value = resultLoader.loadResult();
    }
    return value;
  }

  
  /**
   * 
   * 如果支持缓存，
   * 	尝试缓存加载value，并且将value赋值到resultobject里边
   * 如果支持懒加载，
   *  	将初始化的loadpair添加到loadermap里边
   * 如果都不支持,
   * 	直接查询value
   * 
   * 总结：获取嵌套查询属性对应的value并且返回
   * 
   * @param rs
   * @param metaResultObject
   * @param propertyMapping
   * @param lazyLoader
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getNestedQueryMappingValue(ResultSet rs, MetaObject metaResultObject, ResultMapping propertyMapping, ResultLoaderMap lazyLoader, String columnPrefix)
      throws SQLException {
    final String nestedQueryId = propertyMapping.getNestedQueryId();
    final String property = propertyMapping.getProperty();
    final MappedStatement nestedQuery = configuration.getMappedStatement(nestedQueryId);
    final Class<?> nestedQueryParameterType = nestedQuery.getParameterMap().getType();
    final Object nestedQueryParameterObject = prepareParameterForNestedQuery(rs, propertyMapping, nestedQueryParameterType, columnPrefix);
    Object value = NO_VALUE;
    if (nestedQueryParameterObject != null) {
      final BoundSql nestedBoundSql = nestedQuery.getBoundSql(nestedQueryParameterObject);
      //生成唯一cachekey
      final CacheKey key = executor.createCacheKey(nestedQuery, nestedQueryParameterObject, RowBounds.DEFAULT, nestedBoundSql);
      final Class<?> targetType = propertyMapping.getJavaType();
      //如果key被缓存过，就尝试从缓存加载value到resultobject
      if (executor.isCached(nestedQuery, key)) {
        executor.deferLoad(nestedQuery, metaResultObject, property, key, targetType);
      } else {
    	//初始化resultloader
        final ResultLoader resultLoader = new ResultLoader(configuration, executor, nestedQuery, nestedQueryParameterObject, targetType, key, nestedBoundSql);
        //如果启用了懒加载，初始化一个loadpair到loadermap里边，如果没有启用，直接获取value并且返回
        if (propertyMapping.isLazy()) {
          lazyLoader.addLoader(property, metaResultObject, resultLoader);
        } else {//直接加载
          value = resultLoader.loadResult();
        }
      }
    }
    return value;
  }

  private Object prepareParameterForNestedQuery(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    if (resultMapping.isCompositeResult()) {
      return prepareCompositeKeyParameter(rs, resultMapping, parameterType, columnPrefix);
    } else {
      return prepareSimpleKeyParameter(rs, resultMapping, parameterType, columnPrefix);
    }
  }

  private Object prepareSimpleKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    final TypeHandler<?> typeHandler;
    if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
      typeHandler = typeHandlerRegistry.getTypeHandler(parameterType);
    } else {
      typeHandler = typeHandlerRegistry.getUnknownTypeHandler();
    }
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  private Object prepareCompositeKeyParameter(ResultSet rs, ResultMapping resultMapping, Class<?> parameterType, String columnPrefix) throws SQLException {
    final Object parameterObject = instantiateParameterObject(parameterType);
    final MetaObject metaObject = configuration.newMetaObject(parameterObject);
    boolean foundValues = false;
    for (ResultMapping innerResultMapping : resultMapping.getComposites()) {
      final Class<?> propType = metaObject.getSetterType(innerResultMapping.getProperty());
      final TypeHandler<?> typeHandler = typeHandlerRegistry.getTypeHandler(propType);
      final Object propValue = typeHandler.getResult(rs, prependPrefix(innerResultMapping.getColumn(), columnPrefix));
      if (propValue != null) { // issue #353 & #560 do not execute nested query if key is null
        metaObject.setValue(innerResultMapping.getProperty(), propValue);
        foundValues = true;
      }
    }
    return foundValues ? parameterObject : null;
  }

  private Object instantiateParameterObject(Class<?> parameterType) {
    if (parameterType == null) {
      return new HashMap<Object, Object>();
    } else {
      return objectFactory.create(parameterType);
    }
  }

  //
  // DISCRIMINATOR
  //
  
  /**
   * 处理resultmap里边的Discriminator(switch)情况,将resultmap返回
   * @param rs
   * @param resultMap
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  public ResultMap resolveDiscriminatedResultMap(ResultSet rs, ResultMap resultMap, String columnPrefix) throws SQLException {
    Set<String> pastDiscriminators = new HashSet<String>();
    //有时候一条数据库查询可能会返回包括各种不同的数据类型的结果集。Discriminator（识别器）元素被设计来处理这种情况，
    // 以及其它像类继承层次情况。识别器非常好理解，它就像java里的switch语句。
    Discriminator discriminator = resultMap.getDiscriminator();
    //如果discriminator不为空
    while (discriminator != null) {
    	//TODO:从resultset中获取discriminator包装的column的值
      final Object value = getDiscriminatorValue(rs, discriminator, columnPrefix);
      //根据获取的value,从discriminator的返回值map里边获取对应的resultmapid
      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
      //从configuration中根据mapid判断是否有resultmap
      if (configuration.hasResultMap(discriminatedMapId)) {
    	 //获取resultmap
        resultMap = configuration.getResultMap(discriminatedMapId);
        //判断discriminator中获取的resultmap是否还有Discriminator,如果有,赋值给discriminator,并且继续while执行
        Discriminator lastDiscriminator = discriminator;
        discriminator = resultMap.getDiscriminator();
        if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
          break;
        }
      } else {
        break;
      }
    }
    //将处理后的resultmap返回
    return resultMap;
  }

  
  /**
   * 从resultset中获取discriminator包装的column的值
   * @param rs
   * @param discriminator
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private Object getDiscriminatorValue(ResultSet rs, Discriminator discriminator, String columnPrefix) throws SQLException {
    final ResultMapping resultMapping = discriminator.getResultMapping();
    final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
    //根据columnname从resultset里边获取value
    return typeHandler.getResult(rs, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  
  /**
   * 如果有prefix,将返回(prefix+columnName),否则返回columnname
   * @param columnName
   * @param prefix
   * @return
   */
    private String prependPrefix(String columnName, String prefix) {
        if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
          return columnName;
        }
        return prefix + columnName;
      }

  //
  // HANDLE NESTED RESULT MAPS
  //

  private void handleRowValuesForNestedResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
	  //初始化DefaultResultContext
    final DefaultResultContext resultContext = new DefaultResultContext();
    //根据RowBounds来将resultset跳转到第offset行
    skipRows(rsw.getResultSet(), rowBounds);
    Object rowValue = null;
    //判断是否还有更多的row要处理 && resultset.next()为true
    while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
      //处理resultmap里边的Discriminator情况,将resultmap返回
      final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
      //columnprefix表示在userResultMap里的column全都加上prefix,以跟select语句里column label的匹配
      //<association property="user" resultMap="userResultMap" columnPrefix="user_"/>
      //为每个resultset创建一个唯一的cachekey
      final CacheKey rowKey = createRowKey(discriminatedResultMap, rsw, null);
      //TODO:尝试从nestedResultObjects通过cachekey获取缓存结果,partialObject可能代表嵌套
      Object partialObject = nestedResultObjects.get(rowKey);
      //TODO:如果isResultOrdered是true
      if (mappedStatement.isResultOrdered()) { // issue #577 && #542
    	//如果没有根据缓存key获取到结果 && rowValue 不为空,为rowvalue创建缓存结果,  cachekey -- rowvalue
        if (partialObject == null && rowValue != null) {
          //先清除缓存结果
          nestedResultObjects.clear();
          
          storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
        //
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, rowKey, null, partialObject);
      } else {
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, rowKey, null, partialObject);
        if (partialObject == null) {
          storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
      }
    }
    if (rowValue != null && mappedStatement.isResultOrdered() && shouldProcessMoreRows(resultContext, rowBounds)) {
      storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
    }
  }
  
  //
  // GET VALUE FROM ROW FOR NESTED RESULT MAP
  //
  /**
   * 1.获取那些自动匹配（驼峰法）的属性value，然后赋值到metaobject
   * 
   * 2.获取那些在xml文件或者@results中设置的匹配属性
   * 	缓存获取
   * 	懒加载（事件触发获取）
   * 	直接获取
   * 
   * 总结：
   * 获取resultset一行数据的value，并且赋值到partialObject返回
   * 
   * @param rsw
   * @param resultMap
   * @param combinedKey
   * @param absoluteKey
   * @param columnPrefix
   * @param partialObject
   * @return
   * @throws SQLException
   */
  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap, CacheKey combinedKey, CacheKey absoluteKey, String columnPrefix, Object partialObject) throws SQLException {
    final String resultMapId = resultMap.getId();
    Object resultObject = partialObject;
    //如果存在嵌套的情况
    if (resultObject != null) {
      //使用resultobject构建MetaObject对象
      final MetaObject metaObject = configuration.newMetaObject(resultObject);
      //缓存resultobject(祖先)到ancestorObjects
      // 缓存resultmapId -- columnPrefix到ancestorColumnPrefix
      putAncestor(absoluteKey, resultObject, resultMapId, columnPrefix);
      
      applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, false);
      ancestorObjects.remove(absoluteKey);
    } else {
      final ResultLoaderMap lazyLoader = new ResultLoaderMap();
      resultObject = createResultObject(rsw, resultMap, lazyLoader, columnPrefix);
      if (resultObject != null && !typeHandlerRegistry.hasTypeHandler(resultMap.getType())) {
        final MetaObject metaObject = configuration.newMetaObject(resultObject);
        boolean foundValues = resultMap.getConstructorResultMappings().size() > 0;
        //获取那些自动匹配（驼峰法）的属性value，然后赋值到metaobject
        if (shouldApplyAutomaticMappings(resultMap, AutoMappingBehavior.FULL.equals(configuration.getAutoMappingBehavior()))) {
          foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, columnPrefix) || foundValues;
        }        
        //获取那些在xml文件或者@results中设置的匹配属性value
        foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, columnPrefix) || foundValues;
        putAncestor(absoluteKey, resultObject, resultMapId, columnPrefix);
        foundValues = applyNestedResultMappings(rsw, resultMap, metaObject, columnPrefix, combinedKey, true) || foundValues;
        ancestorObjects.remove(absoluteKey);
        foundValues = lazyLoader.size() > 0 || foundValues;
        resultObject = foundValues ? resultObject : null;
      }
      if (combinedKey != CacheKey.NULL_CACHE_KEY) nestedResultObjects.put(combinedKey, resultObject);
    }
    return resultObject;
  }

  
  /**
   * 缓存resultobject(祖先,id="ancestor")到ancestorObjects
   * 缓存resultmapId -- columnPrefix到ancestorColumnPrefix
   * 
   * <resultmap ...  id="ancestor">
   * 		<resultmapping  id = 'zzz' javaType="Author"  resultmapId='yy' columnPrefix='xxx'/>
   * </resultmap>
   * 那么这种情况下,应该是resultmap - yy的所有resultmapping都要以 xxx开头
   * 
   * @param rowKey
   * @param resultObject
   * @param resultMapId
   * @param columnPrefix
   */
  private void putAncestor(CacheKey rowKey, Object resultObject, String resultMapId, String columnPrefix) {
	//如果ancestorColumnPrefix集合中不包含resultMapId,将  resultMapId--columnPrefix  存储到ancestorColumnPrefix里
	//可能的情况:
	/*
	 * 
	 * <resultmap ...>
	 * 		<resultmapping  id = '' javaType="Author"  resultmapId='yy' columnPrefix='xxx'/>
	 * </resultmap>
	 * 
	 * 那么这种情况下,应该是resultmap - yy的所有resultmapping都要以 xxx开头
	 */
    if (!ancestorColumnPrefix.containsKey(resultMapId)) {
      ancestorColumnPrefix.put(resultMapId, columnPrefix);
    }
    //通过  rowkey-resultObject ,缓存到ancestorObjects里边
    //可能指的是上面例子的javatype
    ancestorObjects.put(rowKey, resultObject);
  }

  //
  // NESTED RESULT MAP (JOIN MAPPING)嵌套
  //
  
  /**
   * 
   * 遍历resultmap中的propertyresultmapping
   * 处理字段映射(resultmapping)的嵌套情况(join mapping),如果有嵌套,获取嵌套的rowvalue
   * 
   * 如果resultmapping(字段映射)的java类型是collection的子类,那么就将rowvalue添加到集合里边
   * 如果resultmapping(字段映射)的java类型不是collection的子类,那么就将rowvalue通过metaobject赋值给type类型
   * 
   * @param rsw
   * @param resultMap
   * @param metaObject
   * @param parentPrefix
   * @param parentRowKey
   * @param newObject				getRowValue()方法中默认为false
   * @return
   */
  private boolean applyNestedResultMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String parentPrefix, CacheKey parentRowKey, boolean newObject) {
    boolean foundValues = false;
    //遍历resultmap里边所有的字段resultmapping
    for (ResultMapping resultMapping : resultMap.getPropertyResultMappings()) {
      //尝试获取resultmapping里边的嵌套resultmapid
      final String nestedResultMapId = resultMapping.getNestedResultMapId();
      //如果  有嵌套mapid & resultset为空
      if (nestedResultMapId != null && resultMapping.getResultSet() == null) {
        try {
         //尝试获取resultmapping(字段映射) 列名前缀 并且大写返回
          final String columnPrefix = getColumnPrefix(parentPrefix, resultMapping);
          //通过nestedResultMapId从configuration里边获取resultmap,并且处理resultmap的Discriminator(switch)返回
          final ResultMap nestedResultMap = getNestedResultMap(rsw.getResultSet(), nestedResultMapId, columnPrefix);
          //
          CacheKey rowKey = null;
          Object ancestorObject = null;
          //如果resultmapid有前缀,
          if (ancestorColumnPrefix.containsKey(nestedResultMapId)) {
        	 //为每个resultset创建一个唯一的cachekey,columnPrefix存在的原因是要去掉column(列名)的前缀获取property(字段名),然后要判断type里边有没有这个字段
            rowKey = createRowKey(nestedResultMap, rsw, ancestorColumnPrefix.get(nestedResultMapId));
            //使用cachekey获取缓存的上级对象(resultmap)
            ancestorObject = ancestorObjects.get(rowKey);
          }
          //如果是嵌套对象(上级对象不为空,join mapping)
          if (ancestorObject != null) { 
        	 //默认为false
            if (newObject) metaObject.setValue(resultMapping.getProperty(), ancestorObject);
          } else {
        	//为每个resultset创建一个唯一的cachekey
            rowKey = createRowKey(nestedResultMap, rsw, columnPrefix);
            //如果rowKey && parentRowKey的更新数都>1,就将怕parentrowkey更新到combinekey(rowkey)里边返回
            final CacheKey combinedKey = combineKeys(rowKey, parentRowKey);
            //从nestedResultObjects中通过combinedKey来尝试获取rowvalue
            Object rowValue = nestedResultObjects.get(combinedKey);
            //如果获取到了rowvalue,knownValue=true
            boolean knownValue = (rowValue != null);
            //使用字段映射从metaobject中判断并且获取collection属性value
            final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject);            
            //判断resultmapping的notnullcolumns是否存在至少一个能从resultset中取值的column
            if (anyNotNullColumnHasValue(resultMapping, columnPrefix, rsw.getResultSet())) {
              //TODO:
              rowValue = getRowValue(rsw, nestedResultMap, combinedKey, rowKey, columnPrefix, rowValue);
            //如果rowvalue不为空  & knownValue为false,将
              if (rowValue != null && !knownValue) {
            	//如果collectionProperty是collection类型的字段属性
                if (collectionProperty != null) {
                //使用collectionproperty的实例对象构建metaobject,并且将非空的rowvalue添加到collectionproperty的value里
                  final MetaObject targetMetaObject = configuration.newMetaObject(collectionProperty);
                  targetMetaObject.add(rowValue);
                  
                  //如果collectionproperty不是collection的实例对象,就将rwovalue赋值给resultmapping(字段映射)
                } else {
                  metaObject.setValue(resultMapping.getProperty(), rowValue);
                }
                foundValues = true;
              }
            }
          }
        } catch (SQLException e) {
          throw new ExecutorException("Error getting nested result map values for '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
        }
      }
    }
    return foundValues;
  }

  
  /**
   * 尝试获取resultmapping(字段映射) 列名前缀 并且大写返回
   * 例如
   * <resultmap  ...  columnPrefix = 'xxx'>
   * 	<resultmapping ...  columnprefix='yyy'/>
   * </resultmap>
   * 
   * 返回  XXXYYY
   * 
   * @param parentPrefix
   * @param resultMapping
   * @return
   */
  private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
    final StringBuilder columnPrefixBuilder = new StringBuilder();
    //如果parentPrefix(resultmap)不为空
    if (parentPrefix != null) columnPrefixBuilder.append(parentPrefix);
    //如果resultmapping有属性设置columnprefix
    if (resultMapping.getColumnPrefix() != null) columnPrefixBuilder.append(resultMapping.getColumnPrefix());
    //如果有值,大写columnprefix
    final String columnPrefix = columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
    return columnPrefix;
  }

  /**
   * any(任意的,任何一个)
   * 
   * 判断resultmapping的notnullcolumns是否存在至少一个能从resultset中取值的column
   * 
   * @param resultMapping
   * @param columnPrefix
   * @param rs
   * @return
   * @throws SQLException
   */
  private boolean anyNotNullColumnHasValue(ResultMapping resultMapping, String columnPrefix, ResultSet rs) throws SQLException {
    Set<String> notNullColumns = resultMapping.getNotNullColumns();
    boolean anyNotNullColumnHasValue = true;
    //resultmapping的notnullcolumns不为空
    if (notNullColumns != null && !notNullColumns.isEmpty()) {
      anyNotNullColumnHasValue = false;
      //遍历,尝试从resultset中取值,如果value不为null,就满足条件,返回true
      for (String column: notNullColumns) {
        rs.getObject(prependPrefix(column, columnPrefix));
        if (!rs.wasNull()) {
          anyNotNullColumnHasValue = true;
          break;
        }
      }
    }
    return anyNotNullColumnHasValue;
  }

  
  /**
   * 通过nestedResultMapId从configuration里边获取resultmap,并且处理resultmap的Discriminator(switch)返回
   * @param rs
   * @param nestedResultMapId
   * @param columnPrefix
   * @return
   * @throws SQLException
   */
  private ResultMap getNestedResultMap(ResultSet rs, String nestedResultMapId, String columnPrefix) throws SQLException {
	  //从configuration中通过嵌套resultmapid获取resultmap
    ResultMap nestedResultMap = configuration.getResultMap(nestedResultMapId);
    //处理resultmap里边的Discriminator(switch)情况,将resultmap返回
    nestedResultMap = resolveDiscriminatedResultMap(rs, nestedResultMap, columnPrefix);
    return nestedResultMap;
  }

  //
  // UNIQUE RESULT KEY
  //
  
  /**
   * 为每个resultset创建一个唯一的cachekey
   * 
   * 当前resultset(数据库返回列) & resultmapping(类属性或者嵌套属性stu.id)  没有并集
   * 	--返回类型是map
   * 	--尝试从resultset中获取不是并集的columnname的value,将列名和value更新到cachekey中
   * 
   *  当前resultset(数据库返回列) & resultmapping(类属性或者嵌套属性stu.id)  有并集
   * 	--	将当前resultset(数据库返回列) & resultmapping(类属性或者嵌套属性stu.id)  并集 ,遍历所有有value的,将其更新到cachekey里边
   * 
   * @param resultMap
   * @param rsw
   * @param columnPrefix		存在的原因是要去掉column(列名)的前缀获取property(字段名),然后要判断type里边有没有这个字段
   * @return
   * @throws SQLException
   */
  private CacheKey createRowKey(ResultMap resultMap, ResultSetWrapper rsw, String columnPrefix) throws SQLException {
    final CacheKey cacheKey = new CacheKey();
    cacheKey.update(resultMap.getId());
    //从resultmap中获取getIdResultMappings,如果结果为空,就返回getPropertyResultMappings
    List<ResultMapping> resultMappings = getResultMappingsForRowKey(resultMap);
    //如果返回映射没有设置,为0的情况下
    if (resultMappings.size() == 0) {
      //返回的结果类型,例如:Author,如果是map的子类
      if (Map.class.isAssignableFrom(resultMap.getType())) {
    	  //将ResultSetWrapper中所有有值的columname & value 更新到cachekey里边
        createRowKeyForMap(rsw, cacheKey);
      } else {
    	  //如果结果返回类型,例如Author不是map的子类
    	// 获取当前对象resultsetwrapper包装的resultset不包含的resultmap(resultmapping)的字段集合
    	 //  * 遍历集合,尝试从resultset中获取columnname的value,将列名和value更新到cachekey中
        createRowKeyForUnmappedProperties(resultMap, rsw, cacheKey, columnPrefix);
      }
    } else {
      //为每个数据行(resultset)的并集创建一个cachekey
      // 将当前resultset(数据库返回列) & resultmapping(类属性或者嵌套属性stu.id)  并集 ,遍历所有有value的,将其更新到cachekey里边
      createRowKeyForMappedProperties(resultMap, rsw, cacheKey, resultMappings, columnPrefix);
    }
    return cacheKey;
  }

  
  /**
   * 如果rowKey && parentRowKey的更新数都>1,就将怕parentrowkey更新到combinekey(rowkey)里边返回
   * @param rowKey
   * @param parentRowKey
   * @return
   */
  private CacheKey combineKeys(CacheKey rowKey, CacheKey parentRowKey) {
	  
	//如果rowvalue生成的唯一rowkey > 1 && parentRowKey>1
    if (rowKey.getUpdateCount() > 1 && parentRowKey.getUpdateCount() > 1) {
      CacheKey combinedKey;
      try {
    	//combine拷贝rowkey
        combinedKey = rowKey.clone();
      } catch (CloneNotSupportedException e) {
        throw new ExecutorException("Error cloning cache key.  Cause: " + e, e);
      }
      //将parentrowkey更新到combinedKey里边返回
      combinedKey.update(parentRowKey);
      return combinedKey;
    }
    //返回空cachekey
    return CacheKey.NULL_CACHE_KEY;
  }

  
  /**
   * 从resultmap中获取getIdResultMappings,如果结果为空,就返回getPropertyResultMappings
   * @param resultMap
   * @return
   */
  private List<ResultMapping> getResultMappingsForRowKey(ResultMap resultMap) {
    List<ResultMapping> resultMappings = resultMap.getIdResultMappings();
    if (resultMappings.size() == 0) {
      resultMappings = resultMap.getPropertyResultMappings();
    }
    return resultMappings;
  }

  
  /**
   * 为每个数据行(resultset)的并集创建一个cachekey
   * 将当前resultset(数据库返回列) & resultmapping(类属性或者嵌套属性stu.id)  并集 ,遍历所有有value的,将其更新到cachekey里边
   * @param resultMap
   * @param rsw
   * @param cacheKey
   * @param resultMappings
   * @param columnPrefix
   * @throws SQLException
   */
  private void createRowKeyForMappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, List<ResultMapping> resultMappings, String columnPrefix) throws SQLException {
	//遍历所有的resultmapping  
    for (ResultMapping resultMapping : resultMappings) {
      //TODO:如果resultmapping里边有嵌套的resultmap && resultmapping的resultset为空
      if (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null) { // Issue #392
    	//通过嵌套mapid获取resultmap
        final ResultMap nestedResultMap = configuration.getResultMap(resultMapping.getNestedResultMapId());
        //通过嵌套的resultmap,来获取匹配的类类型column & value,通过column & value来获取cachekey
        createRowKeyForMappedProperties(nestedResultMap, rsw, cacheKey, nestedResultMap.getConstructorResultMappings(),
            prependPrefix(resultMapping.getColumnPrefix(), columnPrefix));
        
        //如果嵌套查询id为空的话
      } else if (resultMapping.getNestedQueryId() == null) {
    	//如果有prefix,将返回(prefix+columnName),否则返回columnname
        final String column = prependPrefix(resultMapping.getColumn(), columnPrefix);
        //一个resultmapping对应一个类属性,获取resultmapping(字段)的typehandler
        final TypeHandler<?> th = resultMapping.getTypeHandler();
        //通过(resultmapId:columnPrefix)获取当前resultset(数据库返回列) & resultmapping(类属性)  并集
        List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
        //如果mappedColumnNames并集里边有column
        if (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) { // Issue #114
          //通过typehandler获取resultset里边column的值,并且转换类型
          final Object value = th.getResult(rsw.getResultSet(), column);
          //如果有值,将column & value更新到cachekey里边
          if (value != null) {
            cacheKey.update(column);
            cacheKey.update(value);
          }
        }
      }
    }
  }

  
  /**
   * 获取当前对象resultsetwrapper包装的resultset不包含的resultmap(resultmapping)的字段集合
   * 遍历集合,尝试从resultset中获取columnname的value,将列名和value更新到cachekey中
   * @param resultMap
   * @param rsw
   * @param cacheKey
   * @param columnPrefix				存在的原因是要去掉column(列名)的前缀获取property(字段名),然后要判断type里边有没有这个字段
   * @throws SQLException
   */
  private void createRowKeyForUnmappedProperties(ResultMap resultMap, ResultSetWrapper rsw, CacheKey cacheKey, String columnPrefix) throws SQLException {
	//这个resultmap.getType()指的是例如Author.class
    final MetaClass metaType = MetaClass.forClass(resultMap.getType());
    //当前对象resultsetwrapper包装的resultset不包含的resultmap(resultmapping)的字段集合返回
    List<String> unmappedColumnNames = rsw.getUnmappedColumnNames(resultMap, columnPrefix);
    //遍历没有匹配的columnnames
    for (String column : unmappedColumnNames) {
      String property = column;
      //column前缀columnPrefix不为空的情况下
      if (columnPrefix != null && columnPrefix.length() > 0) {
        // When columnPrefix is specified,
        // ignore columns without the prefix.
    	  //如果column的大写是以columnprefix开头的,将截取property字段
        if (column.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
          property = column.substring(columnPrefix.length());
        } else {
          continue;
        }
      }
      //从当前metaclass里边通过name获取class的字段名,并且不为空
      if (metaType.findProperty(property, configuration.isMapUnderscoreToCamelCase()) != null) {
    	//通过resultset获取column的value
        String value = rsw.getResultSet().getString(column);
        //如果value不为空的话,将column列名和value更新到cachekey里边
        if (value != null) {
          cacheKey.update(column);
          cacheKey.update(value);
        }
      }
    }
  }

  
  /**
   * 将ResultSetWrapper中所有有值的columname & value 更新到cachekey里边
   * @param rsw
   * @param cacheKey
   * @throws SQLException
   */
  private void createRowKeyForMap(ResultSetWrapper rsw, CacheKey cacheKey) throws SQLException {
	//从rsw中获取resultset中的列名的集合
    List<String> columnNames = rsw.getColumnNames();
    //遍历所有的列名
    for (String columnName : columnNames) {
      //从resultset中获取列名的value
      final String value = rsw.getResultSet().getString(columnName);
      //如果value不为空
      if (value != null) {
        cacheKey.update(columnName);
        cacheKey.update(value);
      }
    }
  }

}
