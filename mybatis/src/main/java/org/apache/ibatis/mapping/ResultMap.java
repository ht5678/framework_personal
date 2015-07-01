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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ResultMap {
  //自定义id
  private String id;
  
  private Class<?> type;
  private List<ResultMapping> resultMappings;
  /*
   * class Student{
   *    private int id1;
   *    private int id2;
   * 	private String name;
   *   private String phone;
   *   private int age;
   * 	public Student(String name, String phone){
   * 		this,name = name
   * 	}
   * }
   * 
   */
  //id1 , id2就会在idResultMappings里边
  private List<ResultMapping> idResultMappings;
  //name  ,  phone就会在constructorResultMappings里边
  private List<ResultMapping> constructorResultMappings;
  //id1,id2,  age就会在propertyResultMappings里边
  private List<ResultMapping> propertyResultMappings;
  private Set<String> mappedColumns;
  private Discriminator discriminator;
  private boolean hasNestedResultMaps;
  private boolean hasNestedQueries;
  //是否自动匹配
  private Boolean autoMapping;

  /**
   * 默认的空构造函数
   */
  private ResultMap() {
  }

  
  public static class Builder {
    
    private ResultMap resultMap = new ResultMap();

    /**
     * 构造函数
     * @param configuration         配置
     * @param id                         自定义id字符串
     * @param type                      返回的结果类型
     * @param resultMappings      TODO:
     */
    public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings) {
      this(configuration, id, type, resultMappings, null);
    }
    
    
    /**
     * 构造函数
     * @param configuration         配置
     * @param id                         自定义id字符串
     * @param type                      返回的结果类型
     * @param resultMappings      TODO:
     * @param autoMapping          是否自动匹配
     */
    public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings, Boolean autoMapping) {
      resultMap.id = id;
      resultMap.type = type;
      resultMap.resultMappings = resultMappings;
      resultMap.autoMapping = autoMapping;
    }

    public Builder discriminator(Discriminator discriminator) {
      resultMap.discriminator = discriminator;
      return this;
    }

    public Class<?> type() {
      return resultMap.type;
    }

    
    /**
     * 组装ResultMap数据
     * @return
     */
    public ResultMap build() {
      //resultMap必须要有id
      if (resultMap.id == null) {
        throw new IllegalArgumentException("ResultMaps must have an id");
      }
      //初始化变量
      resultMap.mappedColumns = new HashSet<String>();
      resultMap.idResultMappings = new ArrayList<ResultMapping>();
      resultMap.constructorResultMappings = new ArrayList<ResultMapping>();
      resultMap.propertyResultMappings = new ArrayList<ResultMapping>();
      
      //遍历所有的resultMappings
      for (ResultMapping resultMapping : resultMap.resultMappings) {
        //有没有查询
        resultMap.hasNestedQueries = resultMap.hasNestedQueries || resultMapping.getNestedQueryId() != null;
        //判断resultmapping是否是嵌套的,就是可能会用到懒加载的那种
        resultMap.hasNestedResultMaps = resultMap.hasNestedResultMaps || (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null);
        //获取字段名
        final String column = resultMapping.getColumn();
        //如果数据库列名不为空
        if (column != null) {
          //将列名变成大写
          resultMap.mappedColumns.add(column.toUpperCase(Locale.ENGLISH));
        } else if (resultMapping.isCompositeResult()) {//如果是混合结果
           //遍历所有的compositeResultMapping，然后将compositeColumn大写组装到resultMap.mappedColumns里边
          for (ResultMapping compositeResultMapping : resultMapping.getComposites()) {
            final String compositeColumn = compositeResultMapping.getColumn();
            if (compositeColumn != null) {
              resultMap.mappedColumns.add(compositeColumn.toUpperCase(Locale.ENGLISH));
            }
          }
        }
        //TODO:如果是构造器类型的，就将resultMapping加入到resultMap.constructorResultMappings
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          resultMap.constructorResultMappings.add(resultMapping);
        } else {
          //如果是其他类型的，就将resultMapping加入到resultMap.propertyResultMappings
          resultMap.propertyResultMappings.add(resultMapping);
        }
        //如果包含id类型，将resultMapping加入到resultMap.idResultMappings
        if (resultMapping.getFlags().contains(ResultFlag.ID)) {
          resultMap.idResultMappings.add(resultMapping);
        }
      }
      
      
      //如果idResultMappings
      if (resultMap.idResultMappings.isEmpty()) {
        resultMap.idResultMappings.addAll(resultMap.resultMappings);
      }
      // lock down collections
      //锁定所有集合
      resultMap.resultMappings = Collections.unmodifiableList(resultMap.resultMappings);
      resultMap.idResultMappings = Collections.unmodifiableList(resultMap.idResultMappings);
      resultMap.constructorResultMappings = Collections.unmodifiableList(resultMap.constructorResultMappings);
      resultMap.propertyResultMappings = Collections.unmodifiableList(resultMap.propertyResultMappings);
      resultMap.mappedColumns = Collections.unmodifiableSet(resultMap.mappedColumns);
      return resultMap;
    }
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public boolean hasNestedQueries() {
    return hasNestedQueries;
  }

  public Class<?> getType() {
    return type;
  }

  public List<ResultMapping> getResultMappings() {
    return resultMappings;
  }

  /**
   * name  ,  phone就会在constructorResultMappings里边
   * @return
   */
  public List<ResultMapping> getConstructorResultMappings() {
    return constructorResultMappings;
  }

  /**
   * id1,id2,  age就会在propertyResultMappings里边
   * @return
   */
  public List<ResultMapping> getPropertyResultMappings() {
    return propertyResultMappings;
  }

  public List<ResultMapping> getIdResultMappings() {
    return idResultMappings;
  }

  public Set<String> getMappedColumns() {
    return mappedColumns;
  }

  public Discriminator getDiscriminator() {
    return discriminator;
  }

  public void forceNestedResultMaps() {
    hasNestedResultMaps = true;
  }
  
  public Boolean getAutoMapping() {
    return autoMapping;
  }

}
