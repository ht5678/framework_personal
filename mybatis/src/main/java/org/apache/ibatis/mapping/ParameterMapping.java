/*
 *    Copyright 2009-2013 the original author or authors.
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

import java.sql.ResultSet;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 
 * 一个ParameterMapping代表一个方法参数的属性
 * 
 * 比如Author类
 * 有id，name,age三个属性
 * 就会有三个ParameterMapping
 * 
 * 
 * @author Clinton Begin
 */
public class ParameterMapping {
  //配置类
  private Configuration configuration;
  //属性名
  private String property;
  //参数类型,默认为In参数
  private ParameterMode mode;
  //java类型
  private Class<?> javaType = Object.class;
  //jdbc类型
  private JdbcType jdbcType;
  
  private Integer numericScale;
  //类型转换器
  private TypeHandler<?> typeHandler;
  
  private String resultMapId;
  private String jdbcTypeName;
  private String expression;

  private ParameterMapping() {
  }

  public static class Builder {
    private ParameterMapping parameterMapping = new ParameterMapping();

    
    /**
     * 构造方法
     * @param configuration     配置类
     * @param property           属性名
     * @param typeHandler      类型处理方法
     */
    public Builder(Configuration configuration, String property, TypeHandler<?> typeHandler) {
      parameterMapping.configuration = configuration;
      parameterMapping.property = property;
      parameterMapping.typeHandler = typeHandler;
      //参数模式,默认为In参数
      parameterMapping.mode = ParameterMode.IN;
    }

    
    /**
     * 构造方法
     * @param configuration     配置类
     * @param property           属性名
     * @param javaType           属性的java类型
     */
    public Builder(Configuration configuration, String property, Class<?> javaType) {
      parameterMapping.configuration = configuration;
      parameterMapping.property = property;
      parameterMapping.javaType = javaType;
      
      parameterMapping.mode = ParameterMode.IN;
    }

    
    
    public Builder mode(ParameterMode mode) {
      parameterMapping.mode = mode;
      return this;
    }

    public Builder javaType(Class<?> javaType) {
      parameterMapping.javaType = javaType;
      return this;
    }

    /**
     * 设置parameterMapping的jdbcType
     * @param jdbcType      jdbc类型
     * @return
     */
    public Builder jdbcType(JdbcType jdbcType) {
      parameterMapping.jdbcType = jdbcType;
      return this;
    }

    public Builder numericScale(Integer numericScale) {
      parameterMapping.numericScale = numericScale;
      return this;
    }

    public Builder resultMapId(String resultMapId) {
      parameterMapping.resultMapId = resultMapId;
      return this;
    }

    public Builder typeHandler(TypeHandler<?> typeHandler) {
      parameterMapping.typeHandler = typeHandler;
      return this;
    }

    public Builder jdbcTypeName(String jdbcTypeName) {
      parameterMapping.jdbcTypeName = jdbcTypeName;
      return this;
    }

    public Builder expression(String expression) {
      parameterMapping.expression = expression;
      return this;
    }

    
    /**
     * 构建一个ParameterMapping
     * @return
     */
    public ParameterMapping build() {
      //给parameterMapping找一个合适的typeHandler（适用于当前的parameterMapping的typeHandler为空并且javaType不为空的情况）
      resolveTypeHandler();
      // 合法性校验
      validate();
      //返回parameterMapping
      return parameterMapping;
    }
    
    
    /**
     * 合法性校验
     * TODO:
     */
    private void validate() {
      if (ResultSet.class.equals(parameterMapping.javaType)) {
        if (parameterMapping.resultMapId == null) { 
          throw new IllegalStateException("Missing resultmap in property '"  
              + parameterMapping.property + "'.  " 
              + "Parameters of type java.sql.ResultSet require a resultmap.");
        }            
      } else {
        if (parameterMapping.typeHandler == null) { 
          throw new IllegalStateException("Type handler was null on parameter mapping for property '"  
              + parameterMapping.property + "'.  " 
              + "It was either not specified and/or could not be found for the javaType / jdbcType combination specified.");
        }
      }
    }

    
    /**
     * 给parameterMapping找一个合适的typeHandler（适用于当前的parameterMapping的typeHandler为空并且javaType不为空的情况）
     * 根据java类型和jdbc类型获取typehandler
     */
    private void resolveTypeHandler() {
      if (parameterMapping.typeHandler == null && parameterMapping.javaType != null) {
        Configuration configuration = parameterMapping.configuration;
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        //根据java类型和jdbc类型获取typehandler
        parameterMapping.typeHandler = typeHandlerRegistry.getTypeHandler(parameterMapping.javaType, parameterMapping.jdbcType);
      }
    }

  }

  public String getProperty() {
    return property;
  }

  /**
   * Used for handling output of callable statements
   * @return
   */
  public ParameterMode getMode() {
    return mode;
  }

  /**
   * Used for handling output of callable statements
   * @return
   */
  public Class<?> getJavaType() {
    return javaType;
  }

  /**
   * Used in the UnknownTypeHandler in case there is no handler for the property type
   * @return
   */
  public JdbcType getJdbcType() {
    return jdbcType;
  }

  /**
   * Used for handling output of callable statements
   * @return
   */
  public Integer getNumericScale() {
    return numericScale;
  }

  /**
   * Used when setting parameters to the PreparedStatement
   * @return
   */
  public TypeHandler<?> getTypeHandler() {
    return typeHandler;
  }

  /**
   * Used for handling output of callable statements
   * @return
   */
  public String getResultMapId() {
    return resultMapId;
  }

  /**
   * Used for handling output of callable statements
   * @return
   */
  public String getJdbcTypeName() {
    return jdbcTypeName;
  }

  /**
   * Not used
   * @return
   */
  public String getExpression() {
    return expression;
  }

}
