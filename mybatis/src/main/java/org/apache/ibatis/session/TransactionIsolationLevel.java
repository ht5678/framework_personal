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

import java.sql.Connection;

/**
 * @author Clinton Begin
 */
public enum TransactionIsolationLevel {
   //TRANSACTION_NONE：此级别不支持事务
  NONE(Connection.TRANSACTION_NONE),
  
  //TRANSACTION_READ_COMMITTED：此级别要求某一事务只能等别的事务全部更改完才能读。可以防止发生脏读，但不可重复读和幻读有可能发生。
  READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
  
  //TRANSACTION_READ_UNCOMMITTED：此级别允许某一事务读其他事务还没有更改完的数据。允许发生脏读、不可重复读和幻读。
  READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
  
  //TRANSACTION_REPEATABLE_READ：此级别要求某一事务只能等别的事务全部更改完才能读而且禁止不可重复读。也就是可以防止脏读和不可重复读，但幻读有可能发生。
  REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
  
  //TRANSACTION_SERIALIZABLE：此级别防止发生脏读、不可重复读和幻读，事务只能一个接着一个地执行，而不能并发执行。
  SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

  private final int level;

  private TransactionIsolationLevel(int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }
}
