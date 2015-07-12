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
package org.apache.ibatis.executor;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);

  protected Transaction transaction;
  protected Executor wrapper;

  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
  protected PerpetualCache localCache;
  protected PerpetualCache localOutputParameterCache;
  protected Configuration configuration;

  protected int queryStack = 0;
  private boolean closed;

  
  /**
   * 构造函数
   * @param configuration   
   * @param transaction
   */
  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<DeferredLoad>();
    
    //初始化PerpetualCache缓存，id为LocalCache
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }
  
  

  public Transaction getTransaction() {
    if (closed) throw new ExecutorException("Executor was closed.");
    return transaction;
  }

  public void close(boolean forceRollback) {
    try {
      try {
        rollback(forceRollback);
      } finally {
        if (transaction != null) transaction.close();
      }
    } catch (SQLException e) {
      // Ignore.  There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  public boolean isClosed() {
    return closed;
  }

  
  /**
   * 对数据库执行更新操作,返回数据库更新行数
   * insert或者update
   */
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    //记录信息
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    //TODO:
    if (closed) throw new ExecutorException("Executor was closed.");
    //清楚本地缓存
    clearLocalCache();
    //对数据库执行更新操作,返回数据库更新行数
    return doUpdate(ms, parameter);
  }

  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) throw new ExecutorException("Executor was closed.");
    return doFlushStatements(isRollBack);
  }

  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    //根据MappedStatement的boundsql和参数parameterObject获取BoundSql
    BoundSql boundSql = ms.getBoundSql(parameter);
    //构建cachekey，并且返回
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
 }

  @SuppressWarnings("unchecked")
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) throw new ExecutorException("Executor was closed.");
    //如果当前没有查询  并且 设置了需要刷新缓存
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }
    
    List<E> list;
    try {
      //查询栈+1
      queryStack++;
      //如果有resulthandler，那么从本地缓存根据cachekey获取resulthandler，否则，list=null
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      //如果从PerpetualCache缓存中获取到了list；否则从数据库中查询
      if (list != null) {
         //如果statementtype类型是CALLABLE类型,通过cachekey从localOutputParameterCache获取查询参数缓存,
    	 // 将缓存中OUT参数和INOUT参数覆盖到parameter里边
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        //从数据库中查询结果集
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      //查询完成，查询栈-1
      queryStack--;
    }
    //如果当前没有查询
    if (queryStack == 0) {
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      deferredLoads.clear(); // issue #601
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        clearLocalCache(); // issue #482
      }
    }
    return list;
  }

  
  /**
   * 如果key被缓存了，就尝试加载，如果key没有被缓存，就将DeferredLoad放到deferredLoads里边，
   * 然后在queryFromDatabase后遍历加载缓存
   * 
   * 总结：
   * 缓存加载value到resultobject
   * 
   */
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    if (closed) throw new ExecutorException("Executor was closed.");
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    //如果key被缓存了，就可以直接加载，并且将value赋值到resultobject中
    if (deferredLoad.canLoad()) {
    	deferredLoad.load();
    } else {
    	//如果key没有被缓存，将DeferredLoad放到deferredLoads中，然后等BaseExecutor.query.(...)执行完以后，会遍历deferredLoads
    	//然后加载缓存的属性并且将value赋值到resultObject
    	deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  
  /**
   * 根据
   *    MappedStatement的id , 
   *    内存分页的offset和limit，
   *    sql语句，
   *    parametermapping(参数)的属性名和值
   * 来构建cachekey,并且返回
   * 
   */
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    if (closed) throw new ExecutorException("Executor was closed.");
    CacheKey cacheKey = new CacheKey();
    //mappedstatement的id
    cacheKey.update(ms.getId());
    //内存分页
    cacheKey.update(rowBounds.getOffset());
    cacheKey.update(rowBounds.getLimit());
    //sql语句
    cacheKey.update(boundSql.getSql());
    //遍历ParameterMapping，每个类里的字段都有一个ParameterMapping，并且把字段的value放到cachekey里边
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    for (int i = 0; i < parameterMappings.size(); i++) { // mimic DefaultParameterHandler logic
      ParameterMapping parameterMapping = parameterMappings.get(i);
      if (parameterMapping.getMode() != ParameterMode.OUT) {
        Object value;
        String propertyName = parameterMapping.getProperty();
        //从parameterObject获取propertyName的value
        //如果boundsql的additionalParameter（map）里边有propertyName，从map里边获取value
        if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
          //如果parameterObject=null，value设置为null
        } else if (parameterObject == null) {
          value = null;
          //如果parameterObject是基本类型，value直接为parameterObject
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
          value = parameterObject;
          
          //使用parameterObject构建MetaObject，利用metaobject获取value
        } else {
          MetaObject metaObject = configuration.newMetaObject(parameterObject);
          value = metaObject.getValue(propertyName);
        }
        //将value放入cachekey
        cacheKey.update(value);
      }
    }
    return cacheKey;
  }    

  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  public void commit(boolean required) throws SQLException {
    if (closed) throw new ExecutorException("Cannot commit, transaction is already closed");
    clearLocalCache();
    flushStatements();
    if (required) {
      transaction.commit();
    }
  }

  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        clearLocalCache();
        flushStatements(true);
      } finally {
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  
  /**
   * 清楚本地缓存
   */
  public void clearLocalCache() {
    //如果executor没有被关闭
    if (!closed) {
      //清楚本地PerpetualCache缓存
      localCache.clear();
      //TODO：
      localOutputParameterCache.clear();
    }
  }

  /**
   * 对数据库执行更新操作
   * @param ms                      TODO:
   * @param parameter
   * @return
   * @throws SQLException
   */
  protected abstract int doUpdate(MappedStatement ms, Object parameter)
      throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback)
      throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException;

  
  /**
   * 关闭statement
   * @param statement
   */
  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  
  /**
   * 如果statementtype类型是CALLABLE类型,通过cachekey从localOutputParameterCache获取查询参数缓存,
   * 将缓存中OUT参数和INOUT参数覆盖到parameter里边
   * @param ms
   * @param key
   * @param parameter
   * @param boundSql
   */
  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter, BoundSql boundSql) {
     //如果statementtype是callable类型的
    if (ms.getStatementType() == StatementType.CALLABLE) {
      //从缓存中通过key获取OUT参数
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      //如果cachedParameter  & parameter都不为空
      if (cachedParameter != null && parameter != null) {
    	//使用cachedParameter  & parameter构建MetaObject对象
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        //遍历bound里边的ParameterMapping,如果不是IN参数的话,就将缓存里边的cacheparamenter值覆盖parameter的value 
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          if (parameterMapping.getMode() != ParameterMode.IN) {
        	//将缓存里边的cacheparamenter值覆盖parameter的value 
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }

  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    //在localCache中新增key的占位符
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      //移除key的占位符
      localCache.removeObject(key);
    }
    //重新新增缓存数据，key-list(查询结果)
    localCache.putObject(key, list);
    //如果statement是callable类型，将out参数缓存
    if (ms.getStatementType() == StatementType.CALLABLE) {
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  
  /**
   * 根据transaction获取数据库连接
   * 并且根据isDebugEnabled判断返回代理或者返回connection
   * @param statementLog
   * @return
   * @throws SQLException
   */
  protected Connection getConnection(Log statementLog) throws SQLException {
     //根据jdbc connection获取connection
    Connection connection = transaction.getConnection();
    //如果statementLog是允许打印日志，用代理包装connection,返回代理
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    } else {
      //返回原生的connection
      return connection;
    }
  }
  
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }
  
  private static class DeferredLoad {

    private final MetaObject resultObject;
    private final String property;
    private final Class<?> targetType;
    private final CacheKey key;
    private final PerpetualCache localCache;
    private final ObjectFactory objectFactory;
    private final ResultExtractor resultExtractor;

    public DeferredLoad(MetaObject resultObject,
                        String property,
                        CacheKey key,
                        PerpetualCache localCache,
                        Configuration configuration,
                        Class<?> targetType) { // issue #781
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    public boolean canLoad() {
      return localCache.getObject(key) != null && localCache.getObject(key) != EXECUTION_PLACEHOLDER;
    }

    public void load() {
      @SuppressWarnings( "unchecked" ) // we suppose we get back a List
      List<Object> list = (List<Object>) localCache.getObject(key);
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      resultObject.setValue(property, value);
    }

  }

}
