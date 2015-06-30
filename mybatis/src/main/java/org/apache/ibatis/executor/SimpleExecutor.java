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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 */
public class SimpleExecutor extends BaseExecutor {

    
  /**
   * 构造函数，调用父类的构造函数
   * @param configuration
   * @param transaction
   */
  public SimpleExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  
  /**
   * 对数据库执行更新操作，insert  update delete,返回数据库更新行数
   */
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      //获取配置类
      Configuration configuration = ms.getConfiguration();
      //初始化RoutingStatementHandler，并且初始化RoutingStatementHandler的delegate，默认初始化PREPAREDStatementHandler
      //如果有插件，安装插件
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      //初始化statement,并且设置配置参数
      stmt = prepareStatement(handler, ms.getStatementLog());
      //使用PreparedStatement执行数据库更新操作,返回更新数据库条数
      return handler.update(stmt);
    } finally {
    	//	关闭statement
      closeStatement(stmt);
    }
  }

  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      //初始化RoutingStatementHandler，并且给RoutingStatementHandler安装插件
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      //初始化statement
      stmt = prepareStatement(handler, ms.getStatementLog());
      
      return handler.<E>query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    return Collections.emptyList();
  }

  
  /**
   * 
   * 获取connection,可能是jdk proxy代理出来的带有日志功能的代理类
   * 
   * 使用connection初始化statement
   * 
   * 将初始化好的statement返回
   * 
   * @param handler				RoutingStatementHandler,在SimpleExcutor.doUpdate()里边初始化的,delegate默认为PreparedStatementHandler
   * @param statementLog		用于代理connection,使得connection具有日志功能
   * @return
   * @throws SQLException
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    //根据transaction获取数据库连接 并且根据isDebugEnabled判断返回代理或者返回connection
    Connection connection = getConnection(statementLog);
    //初始化statement并且返回      
    stmt = handler.prepare(connection);
    //使用parameterHandler给statement赋值
    handler.parameterize(stmt);
    //将初始化好的statement返回
    return stmt;
  }

}
