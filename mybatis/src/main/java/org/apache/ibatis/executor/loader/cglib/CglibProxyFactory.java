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
package org.apache.ibatis.executor.loader.cglib;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.apache.ibatis.executor.loader.AbstractEnhancedDeserializationProxy;
import org.apache.ibatis.executor.loader.AbstractSerialStateHolder;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.loader.WriteReplaceInterface;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class CglibProxyFactory implements ProxyFactory {

  private static final Log log = LogFactory.getLog(CglibProxyFactory.class);
  private static final String FINALIZE_METHOD = "finalize";
  private static final String WRITE_REPLACE_METHOD = "writeReplace";

  public CglibProxyFactory() {
    try {
      Resources.classForName("net.sf.cglib.proxy.Enhancer");
    } catch (Throwable e) {
      throw new IllegalStateException("Cannot enable lazy loading because CGLIB is not available. Add CGLIB to your classpath.", e);
    }
  }

  public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedResultObjectProxyImpl.createProxy(target, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
  }

  public Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedDeserializationProxyImpl.createProxy(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
  }

  public void setProperties(Properties properties) {
  }

  
  /**
   * 使用cglib创建代理对象
   * @param type                            原生的对象类型
   * @param callback                       回调方法，就是实现了MethodInterceptor接口的子类，会有accept方法
   * @param constructorArgTypes    构造函数的参数类型
   * @param constructorArgs            构造函数的参数值
   * @return
   */
  private static Object crateProxy(Class<?> type, Callback callback, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    Enhancer enhancer = new Enhancer();
    //回调方法，就是实现了MethodInterceptor接口的子类，会有accept方法
    enhancer.setCallback(callback);
    //要生成代理对象的原生类
    enhancer.setSuperclass(type);
    
    
    try {
      //获取目标类型的 writeReplace 方法，如果没有，异常中代理类设置enhancer.setInterfaces(new Class[]{WriteReplaceInterface.class});
      type.getDeclaredMethod(WRITE_REPLACE_METHOD);
      // ObjectOutputStream will call writeReplace of objects returned by writeReplace
      log.debug(WRITE_REPLACE_METHOD + " method was found on bean " + type + ", make sure it returns this");
    } catch (NoSuchMethodException e) {
      enhancer.setInterfaces(new Class[]{WriteReplaceInterface.class});
    } catch (SecurityException e) {
      // nothing to do here
    }
    
    
    Object enhanced = null;
    //如果构造函数没有参数，创建代理对象
    if (constructorArgTypes.isEmpty()) {
      enhanced = enhancer.create();
    } else {
      //否则，初始化带有参数的构造函数
      Class<?>[] typesArray = constructorArgTypes.toArray(new Class[constructorArgTypes.size()]);
      Object[] valuesArray = constructorArgs.toArray(new Object[constructorArgs.size()]);
      //创建带有参数的代理对象
      enhanced = enhancer.create(typesArray, valuesArray);
    }
    return enhanced;
  }

  
  
  /**
   * 
   * 类CglibProxyFactory.java的实现描述：cglib的callable回调的类
   * @author yuezhihua 2015年5月19日 下午6:26:05
   */
  private static class EnhancedResultObjectProxyImpl implements MethodInterceptor {
	//要代理的对象类型
    private Class<?> type;
    private ResultLoaderMap lazyLoader;
    private boolean aggressive;
    private Set<String> lazyLoadTriggerMethods;
    private ObjectFactory objectFactory;
    private List<Class<?>> constructorArgTypes;
    private List<Object> constructorArgs;

    /**
     * 构造函数
     * @param type                              
     * @param lazyLoader
     * @param configuration
     * @param objectFactory
     * @param constructorArgTypes
     * @param constructorArgs
     */
    private EnhancedResultObjectProxyImpl(Class<?> type, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    	//要代理的对象类型
      this.type = type;
      this.lazyLoader = lazyLoader;
      //是否蓝家在aggressive，默认为true
      this.aggressive = configuration.isAggressiveLazyLoading();
      //懒加载触发方法  "equals", "clone", "hashCode", "toString" 
      this.lazyLoadTriggerMethods = configuration.getLazyLoadTriggerMethods();
      this.objectFactory = objectFactory;
      this.constructorArgTypes = constructorArgTypes;
      this.constructorArgs = constructorArgs;
    }

    
    /**
     * 构建target类的代理
     * @param target                            要构建代理的对象
     * @param lazyLoader                     
     * @param configuration                  配置类
     * @param objectFactory                
     * @param constructorArgTypes
     * @param constructorArgs
     * @return
     */
    public static Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      //初始化一个EnhancedResultObjectProxyImpl类
      EnhancedResultObjectProxyImpl callback = new EnhancedResultObjectProxyImpl(type, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
      
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    
    /**
     * cglib拦截器实现
     */
    public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
      final String methodName = method.getName();
      try {
        synchronized (lazyLoader) {
        	
          if (WRITE_REPLACE_METHOD.equals(methodName)) {
        	  
        	//要代理的对象类型对象初始化
            Object original = null;
            //有参数初始化
            if (constructorArgTypes.isEmpty()) {
              original = objectFactory.create(type);
            } else {
              //无参数初始化
              original = objectFactory.create(type, constructorArgTypes, constructorArgs);
            }
            // 将sourceBean对象的值赋值到destinationBean，包括父类的字段赋值
            PropertyCopier.copyBeanProperties(type, enhanced, original);
            //lazyLoader.size()保存需要延迟加载属性列表的个数
            if (lazyLoader.size() > 0) {
              return new CglibSerialStateHolder(original, lazyLoader.getProperties(), objectFactory, constructorArgTypes, constructorArgs);
            } else {
              return original;
            }
          } else {
            if (lazyLoader.size() > 0 && !FINALIZE_METHOD.equals(methodName)) {
              if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                //lazyLoader.loadAll 就会触发ResultLoader的loadResult方法完成数据的加载实现。
                lazyLoader.loadAll();
              } else if (PropertyNamer.isProperty(methodName)) {
            	 //将get或set方法名获取property字段名
                final String property = PropertyNamer.methodToProperty(methodName);
                
                if (lazyLoader.hasLoader(property)) {
                  lazyLoader.load(property);
                }
              }
            }
          }
        }
        return methodProxy.invokeSuper(enhanced, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
  }

  private static class EnhancedDeserializationProxyImpl extends AbstractEnhancedDeserializationProxy implements MethodInterceptor {

    private EnhancedDeserializationProxyImpl(Class<?> type, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      super(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }

    public static Object createProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      EnhancedDeserializationProxyImpl callback = new EnhancedDeserializationProxyImpl(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    @Override
    public Object intercept(Object enhanced, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
      final Object o = super.invoke(enhanced, method, args);
      return (o instanceof AbstractSerialStateHolder) ? o : methodProxy.invokeSuper(o, args);
    }

    @Override
    protected AbstractSerialStateHolder newSerialStateHolder(Object userBean, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      return new CglibSerialStateHolder(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }
  }
}
