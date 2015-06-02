/*
 *    Copyright 2009-2011 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class MetaClass {

  //这个类代表的是缓存一些关于class的定义信息，用于方便的映射  属性名-get/set方法
  private Reflector reflector;

  
  /**
   * 构造函数，初始化reflector
   * @param type
   */
  private MetaClass(Class<?> type) {
    this.reflector = Reflector.forClass(type);
  }

  
  /**
   * 获取type的类信息，以MetaClass组装
   * @param type
   * @return
   */
  public static MetaClass forClass(Class<?> type) {
    return new MetaClass(type);
  }

  public static boolean isClassCacheEnabled() {
    return Reflector.isClassCacheEnabled();
  }

  public static void setClassCacheEnabled(boolean classCacheEnabled) {
    Reflector.setClassCacheEnabled(classCacheEnabled);
  }

  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType);
  }

  
  /**
   * 通过属性名查找字段,例如  RICHfield - richField
   * @param name
   * @return
   */
  public String findProperty(String name) {
	//将name校验，然后将name解析后添加到Stringbuilder里边
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  
  /**
   * 从当前metaclass里边通过name获取class的字段名
   * @param name
   * @param useCamelCaseMapping	是否使用了驼峰法命名
   * @return
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
	  //如果使用了骆驼法则,将_替换成空字符串
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    //通过属性名查找字段,例如  RICHfield - richField
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  
  /**
   * 根据属性名name组装PropertyTokenizer，获取属性名name的set方法的参数类型
   * @param name
   * @return
   */
  public Class<?> getSetterType(String name) {
	//根据属性名name组装PropertyTokenizer
    PropertyTokenizer prop = new PropertyTokenizer(name);
   //如果有xx.yyy.zzzz 情况。重新初始化一个metaclass
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
    //根据propertyName从setTypes获取类型
      return reflector.getSetterType(prop.getName());
    }
  }

  
  /**
   * 
   * 根据属性名name组装PropertyTokenizer，并且获取get方法的返回类型
   * 
   * @param name
   * @return
   */
  public Class<?> getGetterType(String name) {
	//解析属性名的 xx.yyy.zzzz 情况和xx[10].yyy情况
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //如果有xx.yyy.zzzz 情况。重新初始化一个metaclass
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    } else {
    	
      //数据属性字段name实例化的PropertyTokenizer来获取属性name的get方法返回字段,解决返回类型可能是collection的问题
      return getGetterType(prop); // issue #506. Resolve the type inside a Collection Object
    }
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType);
  }

  
  
  /**
   * 获取prop的get方法的返回类型,解决返回类型可能是collection的问题
   * @param prop
   * @return
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
	//根据propertyName获取get方法返回的类型
    Class<?> type = reflector.getGetterType(prop.getName());
    
    //如果prop是带有 [index]  这种格式的，并且type是collection的子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
    	//根据propertyName获取它声明类型
      Type returnType = getGenericGetterType(prop.getName());
      //如果返回类型是泛型，ParameterizedType
      if (returnType instanceof ParameterizedType) {
    	//获取返回类型 泛型化的参数类型,例如<Student>
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        //如果泛型化的参数类型只有一个,返回这个参数类型（如果参数类型是泛型，就获取原类型getRawType）
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
            
            //<T>这种情况
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    
    //返回get方法返回的类型
    return type;
  }

  
  
  /**
   * 
   * 
   * 根据propertyName获取它声明类型
   * 
   * 
   * 根据propertyName从getMethods中获取invoker
   * 
   * 如果invoker是MethodInvoker类型的
   * 返回invoker的method的返回类型
   * 
   * 如果是GetFieldInvoker类型的
   * 返回invoker的field的声明类型
   * 
   * @param propertyName
   * @return
   */
  private Type getGenericGetterType(String propertyName) {
    try {
      //根据propertyName从getMethods集合里边获取MethodInvoker
      Invoker invoker = reflector.getGetInvoker(propertyName);
      //如果invoker是MethodInvoker类型的
      if (invoker instanceof MethodInvoker) {
    	//获取invoker的method的值
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        
        //返回invoker的method的返回类型
        return method.getGenericReturnType();
        
        //如果invoker是GetFieldInvoker类型
      } else if (invoker instanceof GetFieldInvoker) {
    	//获取invoker的field的值
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        
        //返回field的类型
        return field.getGenericType();
      }
    } catch (NoSuchFieldException e) {
    } catch (IllegalAccessException e) {
    }
    //返回null
    return null;
  }

  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  
  /**
   * 判断属性名name是否有get方法
   * @param name
   * @return
   */
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    
    //如果有xx.yyy.zzzz 情况。重新初始化一个metaclass
    if (prop.hasNext()) {
      //通过属性名propertyName检查getMethods里边是否有它的get方法
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
    	
      //通过属性名propertyName检查getMethods里边是否有它的get方法
      return reflector.hasGetter(prop.getName());
    }
  }

  
  //根据propertyName从getMethods集合里边获取MethodInvoker
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  
  /**
   * 
   * 使用reflector对属性名name构建，返回builder
   * 
   * 
   * 事实上，name和返回数据可能是一样的，但是也有可能不一样
   * 例如：
   * RICHfield  -  richField
   * 
   * 因为最终是要在reflector中通过caseInsensitivePropertyMap来查找的，caseInsensitivePropertyMap里边的key是字段的大写
   * 所以可以匹配出来
   * 
   * 
   * @param name
   * @param builder
   * @return
   */
	//根据属性名构建PropertyTokenizer
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // xxx.yyyy情况
    if (prop.hasNext()) {
      //在caseInsensitivePropertyMap中获取key为name(大写)的属性名
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        //回调函数
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
    	//在caseInsensitivePropertyMap中获取key为name(大写)的属性名
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

}
