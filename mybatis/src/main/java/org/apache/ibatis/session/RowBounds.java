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
package org.apache.ibatis.session;

/**
 * @author Clinton Begin
 */
public class RowBounds {
  //默认偏移,就是说  select * from student limit 0,10 里边的startIndex
  public final static int NO_ROW_OFFSET = 0;
  //默认一页的数量
  public final static int NO_ROW_LIMIT = Integer.MAX_VALUE;
  //默认的RowBounds
  public final static RowBounds DEFAULT = new RowBounds();

  //自定义偏移量
  private int offset;
  //自定义一页的行数
  private int limit;

  
  /**
   * 默认构造函数
   * 初始化 offset & limit
   */
  public RowBounds() {
    this.offset = NO_ROW_OFFSET;
    this.limit = NO_ROW_LIMIT;
  }

  public RowBounds(int offset, int limit) {
    this.offset = offset;
    this.limit = limit;
  }

  public int getOffset() {
    return offset;
  }

  public int getLimit() {
    return limit;
  }

}
