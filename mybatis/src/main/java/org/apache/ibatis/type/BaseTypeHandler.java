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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * @author Simone Tripodi
 */
public abstract class BaseTypeHandler<T> extends TypeReference<T> implements TypeHandler<T> {

  protected Configuration configuration;

  public void setConfiguration(Configuration c) {
    this.configuration = c;
  }

  /**
   * 给PreparedStatement设置参数
   * 
   * ps 代表PreparedStatement
   * i   代表第几个参数
   * parameter  代表参数value
   * jdbcType   代表jdbc的类型
   */
  public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
      //如果参数为空
    if (parameter == null) {
      //jdbc类型为空就报错
      if (jdbcType == null) {
        throw new TypeException("JDBC requires that the JdbcType must be specified for all nullable parameters.");
      }
      try {
        //设置空参数
        ps.setNull(i, jdbcType.TYPE_CODE);
      } catch (SQLException e) {
        throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . " +
        		"Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. " +
        		"Cause: " + e, e);
      }
    } else {
      //如果参数不为空,就给ps设置非空的参数,具体方法由不同的子类来赋值,例如BigDecimalTypeHandler
      setNonNullParameter(ps, i, parameter, jdbcType);
    }
  }

  
  /**
   * 从数据库查询结果resultset中获取columnName的value
   */
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    //子类实现获取value方法，并且转换成具体类型
    T result = getNullableResult(rs, columnName);
    //如果返回值为空
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  /**
   * 通过columnIndex获取resultset里边的值
   */
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    T result = getNullableResult(rs, columnIndex);
    if (rs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  /**
   * 通过CallableStatement获取columnIndex的数据库值
   */
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    //通过子类获取值并且转换类型
    T result = getNullableResult(cs, columnIndex);
    if (cs.wasNull()) {
      return null;
    } else {
      return result;
    }
  }

  public abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  public abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;

  public abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;

  public abstract T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException;

}
