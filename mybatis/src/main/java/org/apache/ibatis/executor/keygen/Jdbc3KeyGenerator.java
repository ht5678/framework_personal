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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 
 * 在MappedStatement.builder里边初始化，默认生成策略
 * 
 * @author Clinton Begin
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  
  /**
   * 会在初始化BaseStatementHandler，PreparedStatementHandler的时候调用
   */
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }
  
  /**
   * 会在StatementHandler(例如:PreparedStatementHandler)中执行数据库操作(例如:doUpdate)的方法后执行该方法
   * 
   * 将数据库更新操作返回的生成数据列set到参数对象中
   * 
   */
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {

    List<Object> parameters = new ArrayList<Object>();
    parameters.add(parameter);
    //将数据库更新操作返回的生成数据列set到参数对象中
    processBatch(ms, stmt, parameters);
  }

  
  /**
   * 执行数据库更新(insert , update)操作以后,执行该方法
   * statemennt.getGeneratedKeys获取数据库自动生成的数据列
   * 如果返回的数据列是主键,就将值set到参数(例如:@Param("stu")Student stu)的对象里边
   * @param ms
   * @param stmt
   * @param parameters
   */
  public void processBatch(MappedStatement ms, Statement stmt, List<Object> parameters) {
    ResultSet rs = null;
    try {
      /**
       * 获取由于执行此 Statement 对象而创建的所有自动生成的键。如果此 Statement 对象没有生成任何键，
       * 则返回空的 ResultSet 对象。 
	   *注：如果未指定表示自动生成键的列，则 JDBC 驱动程序实现将确定最能表示自动生成键的列。
       */
      rs = stmt.getGeneratedKeys();
      final Configuration configuration = ms.getConfiguration();
      final TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      //获取主键字段名的数组,可能有多个主键
      final String[] keyProperties = ms.getKeyProperties();
      //获取此 ResultSet 对象的列的编号、类型和属性
      final ResultSetMetaData rsmd = rs.getMetaData();
      
      TypeHandler<?>[] typeHandlers = null;
      //如果属性名数组不为空,并且resultset返回值数>=主键的数量,
      if (keyProperties != null && rsmd.getColumnCount() >= keyProperties.length) {
        for (Object parameter : parameters) {
          if (!rs.next()) break; // there should be one row for each statement (also one for each parameter)
          //利用parameter来构建MetaObject对象
          final MetaObject metaParam = configuration.newMetaObject(parameter);
          //遍历keyProperties获取它的type,再获取typehander,将所有的typehandler返回
          if (typeHandlers == null) typeHandlers = getTypeHandlers(typeHandlerRegistry, metaParam, keyProperties);
          //将从数据库获取的主键值value赋值到方法的参数里
          populateKeys(rs, metaParam, keyProperties, typeHandlers);
        }
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    } finally {
      //关闭resultset
      if (rs != null) {
        try {
          rs.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  
  /**
   * 遍历keyProperties获取它的type,再获取typehander,将所有的typehandler返回
   * @param typeHandlerRegistry
   * @param metaParam
   * @param keyProperties
   * @return
   */
  private TypeHandler<?>[] getTypeHandlers(TypeHandlerRegistry typeHandlerRegistry, MetaObject metaParam, String[] keyProperties) {
    TypeHandler<?>[] typeHandlers = new TypeHandler<?>[keyProperties.length];
    
    for (int i = 0; i < keyProperties.length; i++) {
     //如果属性有set方法
      if (metaParam.hasSetter(keyProperties[i])) {
    	 //获取set方法的set类型
        Class<?> keyPropertyType = metaParam.getSetterType(keyProperties[i]);
        //获取类型的TypeHandler
        TypeHandler<?> th = typeHandlerRegistry.getTypeHandler(keyPropertyType);
        typeHandlers[i] = th;
      }
    }
    //返回数据
    return typeHandlers;
  }

  
  /**
   * 将从数据库获取的主键值value赋值到方法的参数里
   * 会将student的id值赋值到student参数对象返回
   * 
   * @Select("insert into foo(name...)  values (#{obj,name}...) ")
   * public int add(@Param("obj")Student stu)
   *  
   * @param rs	
   * @param metaParam					Student构建的MetaObject
   * @param keyProperties					主键数组
   * @param typeHandlers					
   * @throws SQLException
   */
  private void populateKeys(ResultSet rs, MetaObject metaParam, String[] keyProperties, TypeHandler<?>[] typeHandlers) throws SQLException {
    for (int i = 0; i < keyProperties.length; i++) {
      TypeHandler<?> th = typeHandlers[i];
      if (th != null) {
    	 //通过resulthandler获取resultset里边的value
        Object value = th.getResult(rs, i + 1);
        //将从数据库获取的主键值value赋值到方法的参数里
        /**
         * 会将student的id值赋值到student参数对象返回
         * 
         * @Select("insert into foo(name...)  values (#{obj,name}...) ")
         * public int add(@Param("obj")Student stu) 
         * 
         */
        metaParam.setValue(keyProperties[i], value);
      }
    }
  }

}
