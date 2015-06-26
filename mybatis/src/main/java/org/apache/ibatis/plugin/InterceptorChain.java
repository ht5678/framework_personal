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
package org.apache.ibatis.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Clinton Begin
 */
public class InterceptorChain {

  /**
   * 所有的插件，所有实现Interceptor借口的子类
   */
  private final List<Interceptor> interceptors = new ArrayList<Interceptor>();

  
  /**
   * 将插件包装到target上
   * 
   * 
   *将interceptor的实现类上的Intercepts，Signature注解解析并且组装到map里
   *判断map里边是否包含目标类type的所有接口和它父类的一个或多个接口
   *如果有
   * 使用plugin来代理interfaces接口，返回代理对象
   * 
   * @param target
   * @return
   */
  public Object pluginAll(Object target) {
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }

  
  /**
   * 添加插件
   * @param interceptor
   */
  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }
  
  
  /**
   * 获取锁定不可修改的所有插件
   * @return
   */
  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
