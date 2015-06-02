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
package org.apache.ibatis.mapping;

import java.sql.ResultSet;

/**
 * @author Clinton Begin
 */
public enum ResultSetType {
	
  //结果集的游标只能向下滚动  , 只能使用resultSet.getNext()
  FORWARD_ONLY(ResultSet.TYPE_FORWARD_ONLY),
  //结果集的游标可以上下移动，当数据库变化时，当前结果集不变,可以使用resultSet.getNext() && resultSet.getPrevious()
  SCROLL_INSENSITIVE(ResultSet.TYPE_SCROLL_INSENSITIVE),
  //返回可滚动的结果集，当数据库变化时，当前结果集同步改变,可以使用resultSet.getNext() && resultSet.getPrevious()
  SCROLL_SENSITIVE(ResultSet.TYPE_SCROLL_SENSITIVE);

  private int value;

  ResultSetType(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
