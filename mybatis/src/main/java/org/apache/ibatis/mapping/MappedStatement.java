/*
 *    Copyright 2009-2012 the original author or authors.
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
package org.apache.ibatis.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public final class MappedStatement {

  private String resource;
  //配置类
  private Configuration configuration;
  //MappedStatement的id
  private String id;

  private Integer fetchSize;
  //MappedStatement的超时时间
  private Integer timeout;
  //statement类型
  private StatementType statementType;
  //
  private ResultSetType resultSetType;
  //sql相关
  private SqlSource sqlSource;
  //缓存
  private Cache cache;
  //参数map
  private ParameterMap parameterMap;
  private List<ResultMap> resultMaps;
  //查询之前是否要刷新缓存
  private boolean flushCacheRequired;
  //是否使用缓存
  private boolean useCache;
  private boolean resultOrdered;
  //数据库操作类型
  private SqlCommandType sqlCommandType;
  //key的生成策略
  private KeyGenerator keyGenerator;
  //key主键字段名的数组
  private String[] keyProperties;
  //key主键字段列名的数组
  private String[] keyColumns;
  //是否有嵌套的resultmap
  private boolean hasNestedResultMaps;
  private String databaseId;
  private Log statementLog;
//获取默认的解析实例，xml
  private LanguageDriver lang;
  private String[] resultSets;

  
  /**
   * 不允许直接实例化
   */
  private MappedStatement() {
    // constructor disabled
  }

  
  /**
   * 
   * 类MappedStatement.java的实现描述：MappedStatement的实例化类
   * @author yuezhihua 2015年5月18日 下午8:10:47
   */
  public static class Builder {
    private MappedStatement mappedStatement = new MappedStatement();

    /**
     * 构造函数
     * @param configuration         配置类
     * @param id                          MappedStatement的id
     * @param sqlSource               sql相关类,默认为StaticSqlSource
     * @param sqlCommandType    操作类型枚举
     */
    public Builder(Configuration configuration, String id, SqlSource sqlSource, SqlCommandType sqlCommandType) {
      mappedStatement.configuration = configuration;
      mappedStatement.id = id;
      mappedStatement.sqlSource = sqlSource;
      //声明使用哪种statement操作
      mappedStatement.statementType = StatementType.PREPARED;
      //初始化一个ParameterMap，空的ParameterMapping集合
      mappedStatement.parameterMap = new ParameterMap.Builder(configuration, "defaultParameterMap", null, new ArrayList<ParameterMapping>()).build();
      mappedStatement.resultMaps = new ArrayList<ResultMap>();
      //超时时间
      mappedStatement.timeout = configuration.getDefaultStatementTimeout();
      //数据库操作类型
      mappedStatement.sqlCommandType = sqlCommandType;
      //key的生成策略（配置使用key生成策略并且是新增类型的数据库操作）
      mappedStatement.keyGenerator = configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType) ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
      //获取logId
      String logId = id;
      if (configuration.getLogPrefix() != null) logId = configuration.getLogPrefix() + id;
      //日志打印,会在SimpleExecutor的stmt = prepareStatement(handler, ms.getStatementLog());里边使用
      mappedStatement.statementLog = LogFactory.getLog(logId);
      //获取默认的解析实例，xml
      mappedStatement.lang = configuration.getDefaultScriptingLanuageInstance();
    }

    public Builder resource(String resource) {
      mappedStatement.resource = resource;
      return this;
    }

    public String id() {
      return mappedStatement.id;
    }

    
    /**
     * 一个sql语句的参数map
     * @param parameterMap
     * @return
     */
    public Builder parameterMap(ParameterMap parameterMap) {
      mappedStatement.parameterMap = parameterMap;
      return this;
    }

    /**
     * 设置resultmap
     * @param resultMaps    
     * @return
     */
    public Builder resultMaps(List<ResultMap> resultMaps) {
      mappedStatement.resultMaps = resultMaps;
      //设置  是否有嵌套的resultmap
      for (ResultMap resultMap : resultMaps) {
        mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
      }
      return this;
    }

    
    public Builder fetchSize(Integer fetchSize) {
      mappedStatement.fetchSize = fetchSize;
      return this;
    }

    public Builder timeout(Integer timeout) {
      mappedStatement.timeout = timeout;
      return this;
    }

    public Builder statementType(StatementType statementType) {
      mappedStatement.statementType = statementType;
      return this;
    }

    public Builder resultSetType(ResultSetType resultSetType) {
      mappedStatement.resultSetType = resultSetType;
      return this;
    }

    
    /**
     *  设置 mappedStatement 的cache属性
     * @param cache
     * @return
     */
    public Builder cache(Cache cache) {
      mappedStatement.cache = cache;
      return this;
    }

    public Builder flushCacheRequired(boolean flushCacheRequired) {
      mappedStatement.flushCacheRequired = flushCacheRequired;
      return this;
    }

    public Builder useCache(boolean useCache) {
      mappedStatement.useCache = useCache;
      return this;
    }

    public Builder resultOrdered(boolean resultOrdered) {
      mappedStatement.resultOrdered = resultOrdered;
      return this;
    }

    public Builder keyGenerator(KeyGenerator keyGenerator) {
      mappedStatement.keyGenerator = keyGenerator;
      return this;
    }
    
    /**
     * 设置mappedStatement的keyProperties，字段的属性名
     * @param keyProperty
     * @return
     */
    public Builder keyProperty(String keyProperty) {
      mappedStatement.keyProperties = delimitedStringtoArray(keyProperty);
      return this;
    }

    
    /**
     * 设置mappedStatement的keyColumns，字段的数据库列名
     * @param keyProperty
     * @return
     */
    public Builder keyColumn(String keyColumn) {
      mappedStatement.keyColumns = delimitedStringtoArray(keyColumn);
      return this;
    }

    public Builder databaseId(String databaseId) {
      mappedStatement.databaseId = databaseId;
      return this;
    }

    public Builder lang(LanguageDriver driver) {
      mappedStatement.lang = driver;
      return this;
    }

    public Builder resulSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringtoArray(resultSet);
      return this;
    }
    
    
    /**
     * 构建MappedStatement
     * @return
     */
    public MappedStatement build() {
      //判断
      assert mappedStatement.configuration != null;
      assert mappedStatement.id != null;
      assert mappedStatement.sqlSource != null;
      assert mappedStatement.lang != null;
      //锁定集合
      mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
      return mappedStatement;
    }
  }

  /**
   * 获取key的生成策略
   * @return
   */
  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  public SqlCommandType getSqlCommandType() {
    return sqlCommandType;
  }

  public String getResource() {
    return resource;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public Integer getFetchSize() {
    return fetchSize;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public StatementType getStatementType() {
    return statementType;
  }

  public ResultSetType getResultSetType() {
    return resultSetType;
  }

  public SqlSource getSqlSource() {
    return sqlSource;
  }

  public ParameterMap getParameterMap() {
    return parameterMap;
  }

  public List<ResultMap> getResultMaps() {
    return resultMaps;
  }

  public Cache getCache() {
    return cache;
  }

  public boolean isFlushCacheRequired() {
    return flushCacheRequired;
  }

  public boolean isUseCache() {
    return useCache;
  }

  public boolean isResultOrdered() {
    return resultOrdered;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  /**
   * 获取主键字段名的数组
   * @return
   */
  public String[] getKeyProperties() {
    return keyProperties;
  }

  public String[] getKeyColumns() {
    return keyColumns;
  }

  
  /**
   * executor中执行doUpdate或者doSelect的时候会用到
   * @return
   */
  public Log getStatementLog() {
    return statementLog;
  }

  ////获取默认的解析实例，xml
  public LanguageDriver getLang() {
    return lang;
  }

  public String[] getResulSets() {
    return resultSets;
  }
  
  
  /**
   * 根据MappedStatement的boundsql和参数parameterObject获取BoundSql
   * @param parameterObject
   * @return
   */
  public BoundSql getBoundSql(Object parameterObject) {
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    //获取ParameterMapping，这个是SqlSource的子类在getBoundSql的时候获得的
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    //如果没有参数parameterMappings，那么久尝试从parameterMap里边获取，并且重新初始化boundSql
    if (parameterMappings == null || parameterMappings.size() <= 0) {
      boundSql = new BoundSql(configuration, boundSql.getSql(), parameterMap.getParameterMappings(), parameterObject);
    }
    
    // check for nested result maps in parameter mappings (issue #30)
   //检查ParameterMapping中是否有嵌套的ResultMap，初始化hasNestedResultMaps为true
    for (ParameterMapping pm : boundSql.getParameterMappings()) {
      String rmId = pm.getResultMapId();
      if (rmId != null) {
        ResultMap rm = configuration.getResultMap(rmId);
        if (rm != null) {
          hasNestedResultMaps |= rm.hasNestedResultMaps();
        }
      }
    }
    //返回boundSql
    return boundSql;
  }

  /**
   * 将 a,b,c 转换成数组
   * @param in
   * @return
   */
  private static String[] delimitedStringtoArray(String in) {
    if (in == null || in.trim().length() == 0) {
      return null;
    } else {
      String[] answer = in.split(",");
      return answer;
    }
  }

}
