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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 
 * PreparedStatement 可以防止 SQL 注入
 * 
 * 
 * @author Clinton Begin
 */
public class PreparedStatementHandler extends BaseStatementHandler {

    
  /**
   * 构造函数，调用父类BaseStatementHandler的构造方法
   * @param executor
   * @param mappedStatement
   * @param parameter
   * @param rowBounds
   * @param resultHandler
   * @param boundSql
   */
  public PreparedStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  /**
   * 使用PreparedStatement执行数据库更新操作,返回更新数据库条数
   */
  public int update(Statement statement) throws SQLException {
	 
    PreparedStatement ps = (PreparedStatement) statement;
    //执行数据库操作
    ps.execute();
    //获取数据库操作更新的行数
    int rows = ps.getUpdateCount();
    //获取上下文的 参数对象 & 主键声称策略
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    //将数据库更新操作返回的生成数据列set到参数对象中
    keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
    
    //返回数据库更新数量
    return rows;
  }

  public void batch(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.addBatch();
  }

  
  /**
   * 执行查询操作
   */
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
	 //将statement强转换PreparedStatement
    PreparedStatement ps = (PreparedStatement) statement;
    //执行数据库操作,execute()方法stmt.execute(sql)方法会返回两种形式的数据
    //* 	如果sql是查询语句,会返回resultSet
    //* 	如果sql是更新语句,会返回更新列int
    ps.execute();
    
    return resultSetHandler.<E> handleResultSets(ps);
  }

  
  /**
   * 返回一个prepareStatement，并且根据需要设置不同的参数
   */
  protected Statement instantiateStatement(Connection connection) throws SQLException {
	 //获取sql语句
    String sql = boundSql.getSql();
    //如果自增主键方式是Jdbc3KeyGenerator
    if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
      String[] keyColumnNames = mappedStatement.getKeyColumns();
      //如果没有执行sql后要返回的列，就返回自增的id
      if (keyColumnNames == null) {
    	  //这个表示执行sql（insert）以后返回自增的id
    	//connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
        return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
      } else {//如果有keyColumnNames，执行sql后就返回keyColumnNames数组里边的列value
        return connection.prepareStatement(sql, keyColumnNames);
      }
    } else if (mappedStatement.getResultSetType() != null) {
      //PreparedStatement prepareStatement(String sql,
      // int resultSetType,
      //  int resultSetConcurrency)
      //  throws SQLException创建一个 PreparedStatement 对象，该对象将生成具有给定类型和并发性的 ResultSet 对象。
      //此方法与上述 prepareStatement 方法相同，但它允许重写默认结果集类型和并发性。
      //已创建结果集的可保存性可调用 getHoldability() 确定。 
     //**********详细看@ResultSetType  && 
     //**********ResultSet.CONCUR_READ_ONLY   就是类似只读 属性，不可仪更改的啊！不能用结果集更新数据。
     //**********ResultSet.CONCUR_UPDATABLE     ResultSet对象可以执行数据库的新增、修改、和移除。
      return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    } else {
      //默认返回PreparedStatement,TODO:不知道resultsettype & resultset的默认值
      return connection.prepareStatement(sql);
    }
  }

  /**
   * 使用parameterHandler给statement赋值
   */
  public void parameterize(Statement statement) throws SQLException {
    parameterHandler.setParameters((PreparedStatement) statement);
  }

}
