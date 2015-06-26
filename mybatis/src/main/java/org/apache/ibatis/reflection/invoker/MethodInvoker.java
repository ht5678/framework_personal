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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 
 * 类成员属性的get或者set方法执行器，抽象出来的
 * 
 * @author Clinton Begin
 */
public class MethodInvoker implements Invoker {

	
  //如果method是get方法，type是方法的返回值类型
  //如果method是set方法，type是方法的参数
  private Class<?> type;
  //要执行的方法
  private Method method;

  
  /**
   * 构造函数
   * @param method		方法
   */
  public MethodInvoker(Method method) {
    //要执行操作的方法
    this.method = method;
    //如果只有一个参数（get方法），赋值type为参数类型
    if (method.getParameterTypes().length == 1) {
      type = method.getParameterTypes()[0];
    } else {
      //如果是其他情况（set方法），赋值type为method返回类型
      type = method.getReturnType();
    }
  }

  /**
   * 对目标类target执行指定参数args的method方法，set或get方法
   */
  public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
    return method.invoke(target, args);
  }

  
  /**
   * 获取type类型
   */
  public Class<?> getType() {
    return type;
  }
}
