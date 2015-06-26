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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

  private final TypeHandlerRegistry typeHandlerRegistry;

  private final MappedStatement mappedStatement;
  private final Object parameterObject;
  private BoundSql boundSql;
  private Configuration configuration;

  
  /**
   * 构造函数
   * @param mappedStatement
   * @param parameterObject
   * @param boundSql
   */
  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  public Object getParameterObject() {
    return parameterObject;
  }

  
  /**
   * 使用在PreparedStatement(默认)中初始化的parameterHandler获取parameterMappings
   * 
   * 遍历parameterMappings(类属性的信息)获取每个字段的value , javatype , jdbctype
   * 
   * 然后使用typehandler给PreparedStatement设置参数,ps.setParameter(0,?) ...
   */
  public void setParameters(PreparedStatement ps) throws SQLException {
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
    //获取ParameterMapping集合,如果不为空,就遍历集合
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings != null) {
      for (int i = 0; i < parameterMappings.size(); i++) {
        ParameterMapping parameterMapping = parameterMappings.get(i);
        //TODO:如果parameterMapping的mode不是OUT
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          Object value;
          //获取属性名
          String propertyName = parameterMapping.getProperty();
          //属性名name是否在sql需要的参数里边,?
          if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
            value = boundSql.getAdditionalParameter(propertyName);
            
            //如果parameterObject参数对象为空,value=null
          } else if (parameterObject == null) {
            value = null;
            
            //如果parameterObject是基本类型,直接赋值value
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            value = parameterObject;
            
            //其他情况,组装parameterObject的MetaOject对象,获取propertyName的value
          } else {
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
          //获取parameterMapping(类某个字段的信息)的类型(typehandler)
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          //获取parameterMapping(类某个字段的信息)的数据库类型(jdbctype)
          JdbcType jdbcType = parameterMapping.getJdbcType();
          //将jdbcType类型默认为JDBC.OTHER
          if (value == null && jdbcType == null) jdbcType = configuration.getJdbcTypeForNull();
          //通过typehandler给preparedstatement赋值
          typeHandler.setParameter(ps, i + 1, value, jdbcType);
        }
      }
    }
  }

}
