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
package org.apache.ibatis.executor.result;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

/**
 * @author Clinton Begin
 */
public class DefaultResultHandler implements ResultHandler {

  private final List<Object> list;

  
  /**
   * 没有objectfactory参数,使用默认的方法初始化list=new ArrayList<Object>();
   */
  public DefaultResultHandler() {
    list = new ArrayList<Object>();
  }

  
  /**
   * 构造函数初始化
   * 使用objectFactory(defaultobjectfactory)的create方法初始化List
   * create方法根据type实例化对象，参赛类型为null,参数value为null
   * @param objectFactory
   */
  @SuppressWarnings("unchecked")
  public DefaultResultHandler(ObjectFactory objectFactory) {
	//根据type实例化对象，参赛类型为null,参数value为null
    list = objectFactory.create(List.class);
  }

  public void handleResult(ResultContext context) {
    list.add(context.getResultObject());
  }

  public List<Object> getResultList() {
    return list;
  }

}
