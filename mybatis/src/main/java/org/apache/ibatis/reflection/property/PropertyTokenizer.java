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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 
 * 解析属性名的 xx.yyy.zzzz 情况和xx[10].yyy情况
 * 
 * 
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterable<PropertyTokenizer>, Iterator<PropertyTokenizer> {
	
  //类的属性名
  private String name;
  //indexedName = name
  private String indexedName;
  //如果name里边包含  [index] ,那么将赋值index
  private String index;
  //很难说出来，类似于 student.class  对象里边的对象，一对多类似
  private String children;

  
  /**
   * 带有class属性名字的构造函数
   * @param fullname
   */
  public PropertyTokenizer(String fullname) {
	//判断这个属性名是不是有 .   例如student.class
    int delim = fullname.indexOf('.');
    //如果有子对象
    if (delim > -1) {
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    
    
    indexedName = name;
    //如果name里边包含 []  ，赋值index
    delim = name.indexOf('[');
    if (delim > -1) {
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  ////indexedName = name
  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  /**
   * 如果children不为null
   */
  public boolean hasNext() {
    return children != null;
  }

  
  /**
   * 初始化children的PropertyTokenizer
   */
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  
  
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }

  
  public Iterator<PropertyTokenizer> iterator() {
    return this;
  }
}
