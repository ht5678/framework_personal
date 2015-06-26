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
package org.apache.ibatis.reflection;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * @author Clinton Begin
 */
public class MetaObject {

  private Object originalObject;
  private ObjectWrapper objectWrapper;
  private ObjectFactory objectFactory;
  private ObjectWrapperFactory objectWrapperFactory;

  
  /**
   * 私有的构造函数
   * @param object									要组装对象信息的对象
   * @param objectFactory					在SystemMetaObject里边初始化
   * @param objectWrapperFactory	在SystemMetaObject里边初始化
   */
  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;

    
    //初始化objectWrapper
    
    //如果object是ObjectWrapper类型，objectWrapper就赋值object
    if (object instanceof ObjectWrapper) {
      
      this.objectWrapper = (ObjectWrapper) object;
      
      //直接返回false
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
      
      //如果目标对象是map类型，使用MapWrapper来包装
    } else if (object instanceof Map) {
      this.objectWrapper = new MapWrapper(this, (Map) object);
      
      //如果目标类型是collection，使用CollectionWrapper来包装
    } else if (object instanceof Collection) {
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
      
      //默认使用的是BeanWrapper来包装
    } else {
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }

  
  /**
   * 将对象object组装成MetaObject返回
   * @param object
   * @param objectFactory
   * @param objectWrapperFactory
   * @return
   */
  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory) {
	//如果是空对象，则返回MetaObject forObject(NullObject,...)
    if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory);
    }
  }

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

  public String findProperty(String propName, boolean useCamelCaseMapping) {
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }
  
  /**
   * 获取属性name的set方法参数类型
   * @param name
   * @return
   */
  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

  
  /**
   * 使用MethodInvoker根据prop获取目标对象object的value
   * @param name
   * @return
   */
  public Object getValue(String name) {
	 //获取属性名name的构建对象PropertyTokenizer
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //如果有xx.yyy这种情况
    if (prop.hasNext()) {
      //prop.getIndexedName()的值为name,
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      //如果metaValue是空对象，返回Null
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
    	//获取yyy的value
        return metaValue.getValue(prop.getChildren());
      }
    } else {
      //根据prop获取目标对象object的value
      return objectWrapper.get(prop);
    }
  }

  
  /**
   * 对该对象的name字段赋值value
   * @param name
   * @param value
   */
  public void setValue(String name, Object value) {
	  //解析属性名的 xx.yyy.zzzz 情况和xx[10].yyy情况
    PropertyTokenizer prop = new PropertyTokenizer(name);
    
    //如果有 xx.yyy.zzzz 情况，获取yyy的metaObject，然后赋值
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      
      //如果对象是空对象NullObject
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
    	//当value为空，并且prop有xx.yyy.zzzz 情况,当value为空的时候，不实例化child路径的属性
        if (value == null && prop.getChildren() != null) {
          return; // don't instantiate child path if value is null
        } else {
          //TODO:
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      
      //
      metaValue.setValue(prop.getChildren(), value);
    } else {
      objectWrapper.set(prop, value);
    }
  }

  public MetaObject metaObjectForProperty(String name) {
	//如果目标对象是map，通过key=prop。getName()的方式获取对应的value
    Object value = getValue(name);
    
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  public boolean isCollection() {
    return objectWrapper.isCollection();
  }
  
  public void add(Object element) {
    objectWrapper.add(element);
  }

  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }
  
}
