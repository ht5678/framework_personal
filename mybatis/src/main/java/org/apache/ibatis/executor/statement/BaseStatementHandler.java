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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public abstract class BaseStatementHandler implements StatementHandler {

  protected final Configuration configuration;
  protected final ObjectFactory objectFactory;
  protected final TypeHandlerRegistry typeHandlerRegistry;
  protected final ResultSetHandler resultSetHandler;
  protected final ParameterHandler parameterHandler;

  protected final Executor executor;
  protected final MappedStatement mappedStatement;
  protected final RowBounds rowBounds;

  protected BoundSql boundSql;

  
  /**
   * 构造方法
   * @param executor
   * @param mappedStatement
   * @param parameterObject
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   */
  protected BaseStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    //配置类
    this.configuration = mappedStatement.getConfiguration();
    //TODO:
    this.executor = executor;
    
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;
    
    //所有注册的typeHandlerRegistry
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    
    //当boundSql为空的时候，从mappedStatement中初始化boundSql
    if (boundSql == null) { // issue #435, get the key before calculating the statement
      //生成mybatis的主键
      generateKeys(parameterObject);
      //根据参数parameterObject获取BoundSql
      boundSql = mappedStatement.getBoundSql(parameterObject);
    }

    this.boundSql = boundSql;

    this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
    this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);
  }

  public BoundSql getBoundSql() {
    return boundSql;
  }

  public ParameterHandler getParameterHandler() {
    return parameterHandler;
  }

  
  /**
   * 初始化statement并且返回
   */
  public Statement prepare(Connection connection) throws SQLException {
    ErrorContext.instance().sql(boundSql.getSql());
    Statement statement = null;
    try {
      //返回一个prepareStatement，并且根据需要设置不同的参数
      statement = instantiateStatement(connection);
      //设置statement的超市时间
      setStatementTimeout(statement);
      //mysql的fetchsize问题，http://blog.sina.com.cn/s/blog_670620330101n8dz.html
      setFetchSize(statement);
      //返回statement
      return statement;
    } catch (SQLException e) {
    	//发生异常,关闭statement连接
      closeStatement(statement);
      throw e;
    } catch (Exception e) {
    	//发生异常,关闭statement连接
      closeStatement(statement);
      throw new ExecutorException("Error preparing statement.  Cause: " + e, e);
    }
  }

  protected abstract Statement instantiateStatement(Connection connection) throws SQLException;

  
  /**
   * 设置statement的超时时间
   * @param stmt
   * @throws SQLException
   */
  protected void setStatementTimeout(Statement stmt) throws SQLException {
    Integer timeout = mappedStatement.getTimeout();
    Integer defaultTimeout = configuration.getDefaultStatementTimeout();
    if (timeout != null) {
      stmt.setQueryTimeout(timeout);
    } else if (defaultTimeout != null) {
      stmt.setQueryTimeout(defaultTimeout);
    }
  }

  
  /**
   * mysql的fetchsize问题，http://blog.sina.com.cn/s/blog_670620330101n8dz.html
   * @param stmt
   * @throws SQLException
   */
  protected void setFetchSize(Statement stmt) throws SQLException {
    Integer fetchSize = mappedStatement.getFetchSize();
    if (fetchSize != null) {
      stmt.setFetchSize(fetchSize);
    }
  }

  /**
   * 关闭statement连接 
   * @param statement
   */
  protected void closeStatement(Statement statement) {
    try {
      if (statement != null) {
        statement.close();
      }
    } catch (SQLException e) {
      //ignore
    }
  }

  /**
   * mybatis根据parameter生成主键，默认为Jdbc3KeyGenerator
   * @param parameter
   */
  protected void generateKeys(Object parameter) {
    //获取主键的生成策略
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    //存储当前的error上下文
    ErrorContext.instance().store();
    //不做任何操作，空方法
    keyGenerator.processBefore(executor, mappedStatement, null, parameter);
    //恢复之前的error上下文
    ErrorContext.instance().recall();
  }

}
