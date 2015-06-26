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

import java.util.Collections;
import java.util.List;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ParameterMap {

  private String id;
  private Class<?> type;
  private List<ParameterMapping> parameterMappings;

  private ParameterMap() {
  }

  public static class Builder {
    private ParameterMap parameterMap = new ParameterMap();

    /**
     * 构造函数
     * @param configuration             配置类
     * @param id                              ParameterMap的id
     * @param type                          参数类型，比如
     * 
     *      @insert(insert into author values (.....))
     *      public int insert(@Param("author")Author author);
     *      
     *   里边的Author
     * 
     * @param parameterMappings    参数映射mapping信息
     */
    public Builder(Configuration configuration, String id, Class<?> type, List<ParameterMapping> parameterMappings) {
      parameterMap.id = id;
      parameterMap.type = type;
      parameterMap.parameterMappings = parameterMappings;
    }

    public Class<?> type() {
      return parameterMap.type;
    }

    public ParameterMap build() {
      //lock down collections
        //锁定parameterMappings集合
      parameterMap.parameterMappings = Collections.unmodifiableList(parameterMap.parameterMappings);
      return parameterMap;
    }
  }

  public String getId() {
    return id;
  }

  public Class<?> getType() {
    return type;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

}
