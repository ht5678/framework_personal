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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * 
 * @Intercepts({
      @Signature(type = Map.class, method = "get", args = {Object.class})})
 * 
 * jdk proxy代理
 * 
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  //目标对象
  private Object target;
  //拦截器
  private Interceptor interceptor;
  //@Signature注解里边的数据type，method,args
  private Map<Class<?>, Set<Method>> signatureMap;

  
  /**
   * 构造函数
   * @param target			目标对象
   * @param interceptor		拦截器
   * @param signatureMap	@Signature注解里边的数据type，method,args
   */
  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  
  /**
   * 代理包装对象
   * 
   *将interceptor的实现类上的Intercepts，Signature注解解析并且组装到map里
   *判断map里边是否包含目标类type的所有接口和它父类的一个或多个接口
   *如果有
   * 使用plugin来代理interfaces接口，返回代理对象
   * 否则
   * 返回原生的对象
   * @param target			目标对象
   * @param interceptor		拦截器
   * @return
   */
  public static Object wrap(Object target, Interceptor interceptor) {
	//将interceptor的实现类上的Intercepts，Signature注解解析并且组装到map里边反悔
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    //获取目标对象的类
    Class<?> type = target.getClass();
    //判断map里边是否包含目标类type的所有接口和它父类的一个或多个接口，将包含的接口集合以数组形式返回
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    //如果包含要拦截的接口
    if (interfaces.length > 0) {
      //使用plugin来代理interfaces接口，返回代理对象
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));
    }
    //如果不包含，返回原生的目标对象
    return target;
  }

  
  /**
   * 代理类执行metod的时候通过methods判断是否是拦截方法，如果是拦截方法，返回拦截器结果
   */
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      if (methods != null && methods.contains(method)) {
        return interceptor.intercept(new Invocation(target, method, args));
      }
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  
  /**
   * 将interceptor的实现类上的Intercepts，Signature注解解析并且组装到map里边反悔
   * Map<Class<?>, Set<Method>> 
   * Class值得是Signature注解里边的type
   * Method值得是  通过反射，将sig.type要拦截的对象根据sig.method方法名，sig.args参数获取方法对象
   * Set<Method> 是指要拦截的方法集合
   * @param interceptor
   * @return
   */
  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
	//获取Interceptor实现类的Intercepts注解
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    if (interceptsAnnotation == null) { // issue #251
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());      
    }
    
    //获取interceptsAnnotation的Signature数组
    Signature[] sigs = interceptsAnnotation.value();
    //存放Signature注解的数据，例如：Map(要拦截的类)-get（要拦截的方法）
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<Class<?>, Set<Method>>();
    //将Signature注解的数据加入到map里边
    for (Signature sig : sigs) {
      //获取要拦截的对象，例如map对象
      Set<Method> methods = signatureMap.get(sig.type());
      //获取的数据为空，初始化map
      if (methods == null) {
        methods = new HashSet<Method>();
        //将key，要拦截的类，例如map加入到map里边，并且初始化methods对象
        signatureMap.put(sig.type(), methods);
      }
      try {
    	//通过反射，将sig.type要拦截的对象根据sig.method方法名，sig.args参数获取方法对象
        Method method = sig.type().getMethod(sig.method(), sig.args());
        
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  
  /**
   * map 将interceptor的实现类上的Intercepts，Signature注解解析并且组装到map里边
   * type 要拦截的目标对象类
   * 
   * 判断map里边是否包含目标类type的所有接口和它父类的一个或多个接口，将包含的接口集合以数组形式返回
   * 
   * @param type			目标类
   * @param signatureMap	将interceptor的实现类上的Intercepts，Signature注解解析并且组装到map里边
   * @return
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<Class<?>>();
    while (type != null) {
      //遍历目标类的所有接口
      for (Class<?> c : type.getInterfaces()) {
    	//如果注解解析的map里边包含这个借口，将这个借口类加入到interfaces集合里边
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      //目标类赋值为它的父类
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[interfaces.size()]);
  }

}
