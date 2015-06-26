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

/**
 * 
 * STATEMENT:
 * 拥有executeQuery，executeUpdate和execute方法
 * 只能指向静态sql 
 * insert into student(name,age) values ('zhangsan',11)
 * 
 * PrepareStatement(Statement的子类):
 * 拥有executeQuery，executeUpdate和execute方法,还有包含于PreparedStatement对象中的SQL语句可以使用IN参数，
 * 因此可以动态的向SQL命令传入不同的参数值，提高应用程序的效率和灵活性。
 * insert into student(name,age) values (?,?)
 * 
 * 
 * CALLABLEStatement(PrepareStatement的子类)：
 * 用于执行 SQL 存储过程的接口,支持IN参数,OUT参数,INOUT参数
 * 
 * 
 * 
 * 
 * @author Clinton Begin
 */
public enum StatementType {
  STATEMENT, PREPARED, CALLABLE
}
