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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ReflectPermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/*
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 * 
 * 翻译：这个类代表的是缓存一些关于class的定义信息，用于方便的映射  属性名-get/set方法
 * 
 */
/**
 * @author Clinton Begin
 */
public class Reflector {
  //是否缓存类信息
  private static boolean classCacheEnabled = true;
  private static final String[] EMPTY_STRING_ARRAY = new String[0];
  private static final Map<Class<?>, Reflector> REFLECTOR_MAP = new ConcurrentHashMap<Class<?>, Reflector>();

  //哪个类的类信息
  private Class<?> type;
  
  //clazz类，clazz父类里边所有可以get可读的属性名数组
  private String[] readablePropertyNames = EMPTY_STRING_ARRAY;
  //clazz类，clazz父类里边所有可以set可写的属性名数组
  private String[] writeablePropertyNames = EMPTY_STRING_ARRAY;
  
  //所有的setmethod
  private Map<String, Invoker> setMethods = new HashMap<String, Invoker>();
  
  //存放  属性名-MetodInvoker  结构的数据 或者SetFieldInvoker类型的
  private Map<String, Invoker> getMethods = new HashMap<String, Invoker>();
  private Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>();
  //存放  属性名-方法返回类型  结构的数据或者SetFieldInvoker类型的
  private Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>();
  //默认构造参数
  private Constructor<?> defaultConstructor;
  //对所有字段名大小写不敏感的map ， 结构： 属性名大写 - 属性名正常
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();

  
  
  /**
   * 私有构造函数
   * @param clazz		要获取信息的clazz类
   */
  private Reflector(Class<?> clazz) {
	 //type类型赋值
    type = clazz;
    //赋值defaultConstructor
    addDefaultConstructor(clazz);
    //初始化getMethods  ，， getTypes
    addGetMethods(clazz);
    //将cls的set方法加到setMethods  ,  set方法的参数类型添加到setTypes
    addSetMethods(clazz);
    //将clazz类，clazz父类里边的所有字段遍历，如果getMethods，setMethods中有遗漏的，就加进去
    addFields(clazz);
    //clazz类，clazz父类里边所有可以get可读的属性名数组
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    //clazz类，clazz父类里边所有可以set可写的属性名数组
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    
    /**
     * 将大小写不敏感的所有可读&可写的属性名加入到caseInsensitivePropertyMap里边
     */
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  
  /**
   * 赋值clazz默认的构造函数到defaultConstructor
   * @param clazz
   */
  private void addDefaultConstructor(Class<?> clazz) {
	//获取clazz的所有构造函数
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    //遍历所有的构造函数
    for (Constructor<?> constructor : consts) {
      //如果是无参的构造函数
      if (constructor.getParameterTypes().length == 0) {
    	//判断当前类加载器是否有操作私有方法的权限，否则抛出异常
        if (canAccessPrivateMethods()) {
          try {
        	//设置可以操作私有方法，内部判断权限的原理和canAccessPrivateMethods（）方法一样
            constructor.setAccessible(true);
          } catch (Exception e) {
            // Ignored. This is only a final precaution, nothing we can do.
          }
        }
        //如果构造方法可以被访问，赋值defaultConstructor
        if (constructor.isAccessible()) {
          this.defaultConstructor = constructor;
        }
      }
    }
  }

  
  /**
   * 将cls的所有set类型的方法加入到setterMethods, setTypes里边
   * @param cls
   */
  private void addGetMethods(Class<?> cls) {
	 
    Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
    //获取cls的本身，父类，接口，抽象类的所有方法，并且以方法的签名returnType#methodName:argType1,argType2...作为唯一标识去重复，返回cls的所有的方法数组返回
    Method[] methods = getClassMethods(cls);
    
    
    //遍历所有方法，将其以  字段名-方法集合 的结构放到可能带有冲突的conflictingGetters  map里边
    for (Method method : methods) {
      String name = method.getName();
      
      //如果方法是get方法，以get 或者 is开头
      if (name.startsWith("get") && name.length() > 3) {
        if (method.getParameterTypes().length == 0) {
          //根据方法名获取字段名
          name = PropertyNamer.methodToProperty(name);
          //conflictingMethods中添加可能带有冲突的get方法集合 可能带有冲突是因为，可能一个属性字段有多个get方法
          addMethodConflict(conflictingGetters, name, method);
        }
      } else if (name.startsWith("is") && name.length() > 2) {
        if (method.getParameterTypes().length == 0) {
        	//根据方法名获取字段名
          name = PropertyNamer.methodToProperty(name);
        //conflictingMethods中添加可能带有冲突的get方法集合 可能带有冲突是因为，可能一个属性字段有多个get方法
          addMethodConflict(conflictingGetters, name, method);
        }
      }
      
    }
    //解决conflictingGetters里边get方法的冲突
    resolveGetterConflicts(conflictingGetters);
  }

  
  
  
  /**
   * 解决所有属性的get方法冲突
   * 
   * 如果正常，就在getMethods和getTypes里边添加数据
   * 如果不正常，
   *  	如果是连个返回参数是继承管理，getterType是methodType的父类，不处理
   *  
   *  	methodType是getterType的父类，将propName的get方法有getter变成method‘
   *  
   *  在getMethods和getTypes里边添加数据
   *  
   * 
   * @param conflictingGetters
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
	  
	 
    for (String propName : conflictingGetters.keySet()) {
      //根据propName获取它所有的方法集合
      List<Method> getters = conflictingGetters.get(propName);
      //遍历propName所有的get方法集合，如果只有一个get方法，那么是正常的
      Iterator<Method> iterator = getters.iterator();
      Method firstMethod = iterator.next();
      //正常
      if (getters.size() == 1) {
    	//将  属性名-（get或set）方法  添加到getMethods和getTypes方法集合里边
        addGetMethod(propName, firstMethod);
      } else {
    	  
    	 
    	//不正常的
    	
        Method getter = firstMethod;
        Class<?> getterType = firstMethod.getReturnType();
        while (iterator.hasNext()) {
          //如果propName的第一个get方法和第二个get方法是一样的返回类型,抛出异常
          Method method = iterator.next();
          Class<?> methodType = method.getReturnType();
          //两个get方法是一样的返回类型
          if (methodType.equals(getterType)) {
            throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property " 
                + propName + " in class " + firstMethod.getDeclaringClass()
                + ".  This breaks the JavaBeans " + "specification and can cause unpredicatble results.");
          } 
          //如果是连个返回参数是继承管理，getterType是methodType的父类，不处理
          else if (methodType.isAssignableFrom(getterType)) {
            // OK getter type is descendant
          } else if (getterType.isAssignableFrom(methodType)) {
        	  
        	//methodType是getterType的父类，将propName的get方法有getter变成method
            getter = method;
            getterType = methodType;
          } else {
            throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property " 
                + propName + " in class " + firstMethod.getDeclaringClass()
                + ".  This breaks the JavaBeans " + "specification and can cause unpredicatble results.");
          }
        }
        //将  属性名-（get或set）方法  添加到getMethods和getTypes方法集合里边
        addGetMethod(propName, getter);
      }
    }
  }

  
  /**
   * 将  属性名-（get或set）方法  添加到getMethods和getTypes方法集合里边
   * 
   * 
   * 
   * @param name
   * @param method
   */
  private void addGetMethod(String name, Method method) {
	//验证字段的名称合法性
    if (isValidPropertyName(name)) {
      //存储 属性名 的methodInvoker方法
      getMethods.put(name, new MethodInvoker(method));
      //存放 属性名-返回类型 的数据
      getTypes.put(name, method.getReturnType());
    }
  }

  
  /**
   * 将cls的set方法加到setMethods
   * set方法的参数类型添加到setTypes
   * @param cls
   */
  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
    //获取cls，cls的接口，cls的abstract接口，cls的父类，cls父类的接口。。。
   //的所有方法，并且以方法的签名returnType#methodName:argType1,argType2...作为唯一标识
    //去重复，返回cls的所有的方法数组返回
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      
      //处理所有的set方法
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          //将get或set方法名获取property字段名
          name = PropertyNamer.methodToProperty(name);
          //conflictingMethods中添加可能带有冲突的get或set方法集合
          // 可能带有冲突是因为，可能一个属性字段有多个get或者set方法
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    
    //解决conflictingSetters里边一个propName属性有多个set方法的冲突问题 然后初始化setMethods,setTypes
    resolveSetterConflicts(conflictingSetters);
  }

  
  /**
   * 
   * conflictingMethods中添加可能带有冲突的get或set方法集合
   * 可能带有冲突是因为，可能一个属性字段有多个get或者set方法
   * 
   * 存储结构：
   * name-List<Method>
   * 
   * @param conflictingMethods	可能带有冲突的get方法集合
   * @param name							字段名
   * @param method						方法名
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
	//判断conflictingMethods中是否有name的get方法集合，如果没有，初始化ArrayList
    List<Method> list = conflictingMethods.get(name);
    if (list == null) {
      list = new ArrayList<Method>();
      conflictingMethods.put(name, list);
    }
    
    
    list.add(method);
  }

  
  
  /**
   * 
   * 解决conflictingSetters里边一个propName属性有多个set方法的冲突问题
   * 然后初始化setMethods,setTypes
   * 
   * @param conflictingSetters
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
     //获取propName的所有set方法集合
      List<Method> setters = conflictingSetters.get(propName);
      
      Method firstMethod = setters.get(0);
      //正常情况，setters方法里边该属性只有一个set方法
      if (setters.size() == 1) {
    	 //添加set方法到setMethods属性里边，添加参数类型到setTypes里边
        addSetMethod(propName, firstMethod);
      } else {
    	 //根据propName属性名获取它的get方法的返回类型
        Class<?> expectedType = getTypes.get(propName);
        //，如果为空，抛出异常
        if (expectedType == null) {
          throw new ReflectionException("Illegal overloaded setter method with ambiguous type for property "
              + propName + " in class " + firstMethod.getDeclaringClass() + ".  This breaks the JavaBeans " +
              "specification and can cause unpredicatble results.");
        } else {
        	
          //如果可以获取propName的get方法返回类型,比较expectedType和set方法的类型是否相等，如果相等，set方法变成method
          Iterator<Method> methods = setters.iterator();
          Method setter = null;
          while (methods.hasNext()) {
            Method method = methods.next();
            if (method.getParameterTypes().length == 1
                && expectedType.equals(method.getParameterTypes()[0])) {
              setter = method;
              break;
            }
          }
          if (setter == null) {
            throw new ReflectionException("Illegal overloaded setter method with ambiguous type for property "
                + propName + " in class " + firstMethod.getDeclaringClass() + ".  This breaks the JavaBeans " +
                "specification and can cause unpredicatble results.");
          }
          
          //添加set方法到setMethods属性里边，添加参数类型到setTypes里边
          addSetMethod(propName, setter);
        }
      }
    }
  }

  
  /**
   * 添加set方法到setMethods属性里边，添加参数类型到setTypes里边
   * 
   * setMethods数据结构：
   * 属性名-set方法的MethodInvoker
   * 
   * @param name			类的成员属性名
   * @param method		类方法
   */
  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      setTypes.put(name, method.getParameterTypes()[0]);
    }
  }

  
  /**
   * 获取clazz类，clazz父类 的所有属性，如果settersMethod,gettersMethod不包含这个属性，就将这个属性以
   * 
   *  属性名-setFieldInvoker的结构存储到settersMethod,gettersMethod里边
   *  
   *  
   * 
   * @param clazz
   */
  private void addFields(Class<?> clazz) {
	//获取clazz声明的所有字段
    Field[] fields = clazz.getDeclaredFields();
    //遍历所有字段
    for (Field field : fields) {
      //判断如果有权限
      if (canAccessPrivateMethods()) {
        try {
          field.setAccessible(true);
        } catch (Exception e) {
          // Ignored. This is only a final precaution, nothing we can do.
        }
      }
      //field是可以操作的
      if (field.isAccessible()) {
    	  //如果setMethods集合里边  不  包含这个字段
        if (!setMethods.containsKey(field.getName())) {
          // issue #379 - removed the check for final because JDK 1.5 allows
          // modification of final fields through reflection (JSR-133). (JGB)
          // pr #16 - final static can only be set by the classloader
          //翻译：jdk5版本以上允许对final修饰符的字段进行注入,只能执行final 并且 static 的set方法操作
          
          //将遗漏的field字段加到setMethods,setTypes里边
          int modifiers = field.getModifiers();
          if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
            addSetField(field);
          }
        }
        
        
        //如果getMethods里边不包含这个字段的信息
        if (!getMethods.containsKey(field.getName())) {
          //将遗漏的field字段加到setMethods,setTypes里边
          addGetField(field);
        }
      }
    }
    
    //将clazz的父类的所有属性也加到 settersMethod,gettersMethod里边
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  
  /**
   * 将setMethods不包含的，遗漏的Field字段信息加到
   * 
   * setMethods
   * 
   * setTypes
   * 
   * 里边
   * 
   * @param field
   */
  private void addSetField(Field field) {
	//如果字段名校验是合法的
    if (isValidPropertyName(field.getName())) {
      //在setMethods添加数据结构为  属性名-setfieldInvoker  的数据
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      //添加  属性名-属性的类型  的数据
      setTypes.put(field.getName(), field.getType());
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      getTypes.put(field.getName(), field.getType());
    }
  }

  
  /**
   * 验证字段的名称合法性
   * @param name
   * @return
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /*
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   *
   *获取cls，cls的接口，cls的abstract接口，cls的父类，cls父类的接口。。。
   *的所有方法，并且以方法的签名returnType#methodName:argType1,argType2...作为唯一标识
   *去重复，返回cls的所有的方法数组返回
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    HashMap<String, Method> uniqueMethods = new HashMap<String, Method>();
    Class<?> currentClass = cls;
    while (currentClass != null) {
      //将currentClass的所有方法去重复添加到uniqueMethods里边
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods - 
      // because the class may be abstract
      //获取currentClass的所有接口，因为可能会有abstract的接口
      Class<?>[] interfaces = currentClass.getInterfaces();
      //遍历所有接口，将接口类的所有方法去重复添加到uniqueMethods里边
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      //currentClass等于它的父类
      currentClass = currentClass.getSuperclass();
    }
    //将所有不重复的method以数组返回
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }

  
  /**
   * 将methods数组的方法加入到uniqueMethods里边
   * 
   * 存储结构：
   * key: returnType#methodName:argType1,argType2...
   * value:method
   * 
   * uniqueMethods里边没有重复方法
   * 
   * @param uniqueMethods
   * @param methods
   */
  private void addUniqueMethods(HashMap<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
     //如果方法不是桥方法
      if (!currentMethod.isBridge()) {
    	//获取方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        //判断签名在uniqueMethods是否存在并且canAccessPrivateMethods（）可操作，不存在就添加到uniqueMethods里边
        if (!uniqueMethods.containsKey(signature)) {
          //添加权限
          if (canAccessPrivateMethods()) {
            try {
              currentMethod.setAccessible(true);
            } catch (Exception e) {
              // Ignored. This is only a final precaution, nothing we can do.
            }
          }
          
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  
  /**
   * 组装方法的签名
   * 方法签名组成
   * returnType#methodName:argType1,argType2...
   * 
   * @param method
   * @return
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  
  /**
   * 使用安全管理其判断当前的类加载器是否有权限操作类的私有方法
   * 
   * setAccessible（true）方法的内部和这个的原理一样
   * 
   * 它是通过SecurityManager来管理权限的，我们可以启用java.security.manager来判断程序是否具有调用setAccessible()的权限。
   * 
   * ################
   * 默认情况下，内核API和扩展目录的代码具有该权限，而类路径或通过URLClassLoader加载的应用程序不拥有此权限。例如：当我们以这种方式来执行上述程序时将会抛出异常 
    	java.security.AccessControlException:   access   denied
    	 
   * ################
   * 
   * 
   * public final class ReflectPermission
		extends BasicPermission
		反射操作的 Permission 类。ReflectPermission 是一种指定权限，没有动作。当前定义的唯一名称是suppressAccessChecks，它允许取消由反射对象在其使用点上执行的标准 Java 语言访问检查 - 对于 public、default（包）访问、protected、private 成员。
		
		下表提供了允许权限的简要说明，并讨论了授予代码权限的风险。
		权限目标名称	
		suppressAccessChecks
		
		权限允许的内容	
		能够访问类中的字段和调用方法。注意，这不仅包括 public、而且还包括 protected 和 private 字段和方法。
		
		允许此权限的风险
		存在的风险是，通常不可用的信息（也许是保密信息）和方法可能会接受恶意代码访问。
   * 
   * @return
   */
  private static boolean canAccessPrivateMethods() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /*
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  
  /**
   * 根据propertyName从getMethods集合里边获取MethodInvoker
   * @param propertyName
   * @return
   */
  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /*
   * Gets the type for a property setter
   *
   *根据propertyName从setTypes获取类型
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets the type for a property getter
   *
   *根据propertyName获取get方法返回的类型
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /*
   * Gets an array of the writeable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /*
   * Check to see if a class has a writeable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writeable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /*
   * Check to see if a class has a readable property by name
   * 
   * 通过属性名propertyName检查getMethods里边是否有它的get方法
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  
  /**
   * 在caseInsensitivePropertyMap中获取key为name(大写)的属性名
   * @param name
   * @return
   */
  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }

  /*
   * Gets an instance of ClassInfo for the specified class.
   *
   *获取clazz的ClassInfo
   *
   * @param clazz The class for which to lookup the method cache.
   * @return The method cache for the class
   */
  public static Reflector forClass(Class<?> clazz) {
	  //是否缓存类信息到REFLECTOR_MAP中，默认为true
    if (classCacheEnabled) {
      // synchronized (clazz) removed see issue #461
     //从REFLECTOR_MAP中获取缓存的clazz信息
      Reflector cached = REFLECTOR_MAP.get(clazz);
      //如果缓存为空,重新获取clazz的类信息
      if (cached == null) {
    	//组装clazz的信息
        cached = new Reflector(clazz);
        //将类信息缓存
        REFLECTOR_MAP.put(clazz, cached);
      }
      
      return cached;
    } else {
     //如果不允许缓存，直接组装返回reflector
      return new Reflector(clazz);
    }
  }

  public static void setClassCacheEnabled(boolean classCacheEnabled) {
    Reflector.classCacheEnabled = classCacheEnabled;
  }

  public static boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }
}
