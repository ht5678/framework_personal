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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * An actual SQL String got form an {@link SqlSource} after having processed any dynamic content.
 * The SQL may have SQL placeholders "?" and an list (ordered) of an parameter mappings 
 * with the additional information for each parameter (at least the property name of the input object to read 
 * the value from). 
 * </br>
 * Can also have additional parameters that are created by the dynamic language (for loops, bind...).
 */
/**
 * @author Clinton Begin
 */
public class BoundSql {
  //sql语句
  private String sql;
  //参数集合
  private List<ParameterMapping> parameterMappings;
  //TODO:
  private Object parameterObject;
  //其他参数
  private Map<String, Object> additionalParameters;
  //TODO:
  private MetaObject metaParameters;

  
  /**
   * 构造函数
   * @param configuration               配置类
   * @param sql                              sql
   * @param parameterMappings     参数mapping
   * @param parameterObject        参数实体     
   */
  public BoundSql(Configuration configuration, String sql, List<ParameterMapping> parameterMappings, Object parameterObject) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.parameterObject = parameterObject;
    //其他参数为空
    this.additionalParameters = new HashMap<String, Object>();
    this.metaParameters = configuration.newMetaObject(additionalParameters);
  }

  public String getSql() {
    return sql;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

  public Object getParameterObject() {
    return parameterObject;
  }

  
  /**
   * select * from ... where x=? and ...
   * 属性名name是否在sql需要的参数里边,?
   * @param name
   * @return
   */
  public boolean hasAdditionalParameter(String name) {
    return metaParameters.hasGetter(name);
  }

  public void setAdditionalParameter(String name, Object value) {
    metaParameters.setValue(name, value);
  }
  
  /**
   * 根据属性名name获取参数的value
   * @param name
   * @return
   */
  public Object getAdditionalParameter(String name) {
    return metaParameters.getValue(name);
  }
}
