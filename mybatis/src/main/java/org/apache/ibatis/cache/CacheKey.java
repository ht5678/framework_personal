/*
 *    Copyright 2009-2014 the original author or authors.
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
package org.apache.ibatis.cache;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  public static final CacheKey NULL_CACHE_KEY = new NullCacheKey();

  private static final int DEFAULT_MULTIPLYER = 37;
  private static final int DEFAULT_HASHCODE = 17;

  private int multiplier;
  private int hashcode;
  private long checksum;
  private int count;
  private List<Object> updateList;

  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLYER;
    this.count = 0;
    this.updateList = new ArrayList<Object>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  
  /**
   * 将缓存对象添加到updateList里边，缓存的对象数量+1，
   * @param object
   */
  public void update(Object object) {
	  //如果是数组就通过for循环，保证每个对象最终都要调用doUpdate方法
    if (object != null && object.getClass().isArray()) {
      int length = Array.getLength(object);
      for (int i = 0; i < length; i++) {
        Object element = Array.get(object, i);
        //调用doUpdate方法来更新
        doUpdate(element);
      }
    } else {
      doUpdate(object);
    }
  }

  /**
   * 更新cachekey的变量
   * @param object
   */
  private void doUpdate(Object object) {
    int baseHashCode = object == null ? 1 : object.hashCode();
    //缓存对象数量+1
    count++;
    //TODO:
    checksum += baseHashCode;
    
    baseHashCode *= count;
    //算法计算对象的hashcode
    hashcode = multiplier * hashcode + baseHashCode;
    //将缓存对象添加到list中
    updateList.add(object);
  }

  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  
  /**
   * 重写equals方法，通过对比hashcode值，checksum总数，count缓存对象数量
   * 还有updatelist集合的数量和值是否相等来判断两个cachekey是否相等
   */
  public boolean equals(Object object) {
    if (this == object)
      return true;
    if (!(object instanceof CacheKey))
      return false;

    final CacheKey cacheKey = (CacheKey) object;
    //对比hashcode,checksum,count
    if (hashcode != cacheKey.hashcode)
      return false;
    if (checksum != cacheKey.checksum)
      return false;
    if (count != cacheKey.count)
      return false;
    //对比集合里边的所有对象数量和值是否相等
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (thisObject == null) {
        if (thatObject != null)
          return false;
      } else {
        if (!thisObject.equals(thatObject))
          return false;
      }
    }
    return true;
  }

  public int hashCode() {
    return hashcode;
  }

  
  /**
   * obj:缓存对象
   * cachekey的string格式：hashcode:checksum:obj1:obj2...
   * 
   */
  public String toString() {
    StringBuilder returnValue = new StringBuilder().append(hashcode).append(':').append(checksum);
    for (int i = 0; i < updateList.size(); i++) {
      returnValue.append(':').append(updateList.get(i));
    }

    return returnValue.toString();
  }

  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<Object>(updateList);
    return clonedCacheKey;
  }

}
