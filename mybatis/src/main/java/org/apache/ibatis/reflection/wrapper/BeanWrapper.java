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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class BeanWrapper extends BaseWrapper {

 //metaObject的目标对象
  private Object object;
  
  private MetaClass metaClass;

  
  /**
   * 构造函数，初始化BaseWrapper的MetaObject和object，metaClass
   * @param metaObject
   * @param object
   */
  public BeanWrapper(MetaObject metaObject, Object object) {
    super(metaObject);
    //初始化object为metaObject的目标对象
    this.object = object;
    //获取object类型的类信息，以MetaClass组装
    this.metaClass = MetaClass.forClass(object.getClass());
  }

  
  /**
   * 根据prop获取目标对象object的value
   */
  public Object get(PropertyTokenizer prop) {
	  
	//如果是 [index]  情况
    if (prop.getIndex() != null) {
      Object collection = resolveCollection(prop, object);
      //根据prop的属性名name，和索引index，从对象collection中获取Collection接口子类index的value，并且转换成对应的集合类型
      return getCollectionValue(prop, collection);
    } else {
      //从object对象中获取prop属性的value
      return getBeanProperty(prop, object);
    }
  }

  public void set(PropertyTokenizer prop, Object value) {
	//如果有  [index]  情况，集合类型
    if (prop.getIndex() != null) {
       
      Object collection = resolveCollection(prop, object);
      setCollectionValue(prop, collection, value);
    } else {
      //bean类型
      setBeanProperty(prop, object, value);
    }
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    return metaClass.findProperty(name, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return metaClass.getGetterNames();
  }

  public String[] getSetterNames() {
    return metaClass.getSetterNames();
  }

  
  /**
   * 获取属性name的set方法参数类型
   */
  public Class<?> getSetterType(String name) {
	  //解析属性名的 xx.yyy.zzzz 情况和xx[10].yyy情况
    PropertyTokenizer prop = new PropertyTokenizer(name);
    
    //如果有
    if (prop.hasNext()) {
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return metaClass.getSetterType(name);
      } else {
        return metaValue.getSetterType(prop.getChildren());
      }
    } else {
      // 根据属性名name组装PropertyTokenizer，获取属性名name的set方法的参数类型
      return metaClass.getSetterType(name);
    }
  }

  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return metaClass.getGetterType(name);
      } else {
        return metaValue.getGetterType(prop.getChildren());
      }
    } else {
      return metaClass.getGetterType(name);
    }
  }

  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (metaClass.hasSetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasSetter(name);
        } else {
          return metaValue.hasSetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      return metaClass.hasSetter(name);
    }
  }

  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (metaClass.hasGetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasGetter(name);
        } else {
          return metaValue.hasGetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      return metaClass.hasGetter(name);
    }
  }



  /**
   * 
   */
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
    MetaObject metaValue;
    // 获取属性name的set方法参数类型
    Class<?> type = getSetterType(prop.getName());
    try {
      //根据type实例化对象，参数类型为null,参数value为null
      Object newObject = objectFactory.create(type);
      //获取newObject的MetaObject
      metaValue = MetaObject.forObject(newObject, metaObject.getObjectFactory(), metaObject.getObjectWrapperFactory());
      //
      set(prop, newObject);
    } catch (Exception e) {
      throw new ReflectionException("Cannot set value of property '" + name + "' because '" + name + "' is null and cannot be instantiated on instance of " + type.getName() + ". Cause:" + e.toString(), e);
    }
    return metaValue;
  }

  
  /**
   * 从object对象中获取prop属性的value
   * @param prop
   * @param object
   * @return
   */
  private Object getBeanProperty(PropertyTokenizer prop, Object object) {
    try {
      //根据propertyName从getMethods集合里边获取MethodInvoker
      Invoker method = metaClass.getGetInvoker(prop.getName());
      try {
    	  
    	//从object对象中获取prop属性的value
        return method.invoke(object, NO_ARGUMENTS);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new ReflectionException("Could not get property '" + prop.getName() + "' from " + object.getClass() + ".  Cause: " + t.toString(), t);
    }
  }

  
  /**
   * 获取methodinvoker执行赋值操作
   * @param prop
   * @param object
   * @param value
   */
  private void setBeanProperty(PropertyTokenizer prop, Object object, Object value) {
    try {
      Invoker method = metaClass.getSetInvoker(prop.getName());
      Object[] params = {value};
      try {
    	//对目标对象赋值
        method.invoke(object, params);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (Throwable t) {
      throw new ReflectionException("Could not set property '" + prop.getName() + "' of '" + object.getClass() + "' with value '" + value + "' Cause: " + t.toString(), t);
    }
  }

  public boolean isCollection() {
    return false;
  }

  public void add(Object element) {
    throw new UnsupportedOperationException();
  }

  public <E> void addAll(List<E> list) {
    throw new UnsupportedOperationException();
  }

}
