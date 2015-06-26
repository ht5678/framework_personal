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
package org.apache.ibatis.datasource.pooled;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * @author Clinton Begin
 */
class PooledConnection implements InvocationHandler {

  private static final String CLOSE = "close";
  private static final Class<?>[] IFACES = new Class<?>[] { Connection.class };

  private int hashCode = 0;
  //带有连接池的数据源
  private PooledDataSource dataSource;
  //原生的connection
  private Connection realConnection;
  //代理生成的connection
  private Connection proxyConnection;
  //最后一次使用的时间
  private long checkoutTimestamp;
  //创建时间
  private long createdTimestamp;
  //最后一次使用的时间
  private long lastUsedTimestamp;
  //   "" + url + username + password  的hashcode 值
  private int connectionTypeCode;
  //是否要被校验
  private boolean valid;

  /*
   * Constructor for SimplePooledConnection that uses the Connection and PooledDataSource passed in
   *
   *构造函数
   *
   *
   * @param connection - the connection that is to be presented as a pooled connection     数据库连接
   * @param dataSource - the dataSource that the connection is from                             获取connection的datasource
   */
  public PooledConnection(Connection connection, PooledDataSource dataSource) {
    //数据库连接的hashcode
    this.hashCode = connection.hashCode();
    //原生的数据库连接connection
    this.realConnection = connection;
    //connection产生的来源
    this.dataSource = dataSource;
    //时间戳记录
    this.createdTimestamp = System.currentTimeMillis();
    this.lastUsedTimestamp = System.currentTimeMillis();
    
    this.valid = true;
    //Connection类的代理，使用的是当前类PooledConnection做代理
    this.proxyConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), IFACES, this);
  }

  /*
   * Invalidates the connection
   * 设置为不校验
   */
  public void invalidate() {
    valid = false;
  }

  /*
   * Method to see if the connection is usable
   * 判断connection是不是可用的，是否可以ping通
   * @return True if the connection is usable
   */
  public boolean isValid() {
    return valid && realConnection != null && dataSource.pingConnection(this);
  }

  /*
   * Getter for the *real* connection that this wraps
   *
   * @return The connection
   */
  public Connection getRealConnection() {
    return realConnection;
  }

  /*
   * Getter for the proxy for the connection
   *
   * @return The proxy
   */
  public Connection getProxyConnection() {
    return proxyConnection;
  }

  /*
   * Gets the hashcode of the real connection (or 0 if it is null)
   *
   * @return The hashcode of the real connection (or 0 if it is null)
   */
  public int getRealHashCode() {
    if (realConnection == null) {
      return 0;
    } else {
      return realConnection.hashCode();
    }
  }

  /*
   * Getter for the connection type (based on url + user + password)
   *
   * @return The connection type
   */
  public int getConnectionTypeCode() {
    return connectionTypeCode;
  }

  /*
   * Setter for the connection type
   * 设置 "" + url + username + password  的hashcode 值
   * @param connectionTypeCode - the connection type
   */
  public void setConnectionTypeCode(int connectionTypeCode) {
    this.connectionTypeCode = connectionTypeCode;
  }

  /*
   * Getter for the time that the connection was created
   *
   * @return The creation timestamp
   */
  public long getCreatedTimestamp() {
    return createdTimestamp;
  }

  /*
   * Setter for the time that the connection was created
   *
   * @param createdTimestamp - the timestamp
   */
  public void setCreatedTimestamp(long createdTimestamp) {
    this.createdTimestamp = createdTimestamp;
  }

  /*
   * Getter for the time that the connection was last used
   *
   * @return - the timestamp
   */
  public long getLastUsedTimestamp() {
    return lastUsedTimestamp;
  }

  /*
   * Setter for the time that the connection was last used
   *
   * @param lastUsedTimestamp - the timestamp
   */
  public void setLastUsedTimestamp(long lastUsedTimestamp) {
    this.lastUsedTimestamp = lastUsedTimestamp;
  }

  /*
   * Getter for the time since this connection was last used
   *
   * @return - the time since the last use
   */
  public long getTimeElapsedSinceLastUse() {
    return System.currentTimeMillis() - lastUsedTimestamp;
  }

  /*
   * Getter for the age of the connection
   *
   * @return the age
   */
  public long getAge() {
    return System.currentTimeMillis() - createdTimestamp;
  }

  /*
   * Getter for the timestamp that this connection was checked out
   *获取最后一次使用的时间
   * @return the timestamp
   */
  public long getCheckoutTimestamp() {
    return checkoutTimestamp;
  }

  /*
   * Setter for the timestamp that this connection was checked out
   * 设置数据库连接的校验时间，也就是和LastUsedTimestamp一样，就是最后一次开始使用的时间
   * @param timestamp the timestamp
   */
  public void setCheckoutTimestamp(long timestamp) {
    this.checkoutTimestamp = timestamp;
  }

  /*
   * Getter for the time that this connection has been checked out
   * 当前时间-最后一次使用时间   （可以用来查询是否超出connection设置的超时时间）
   * @return the time
   */
  public long getCheckoutTime() {
    return System.currentTimeMillis() - checkoutTimestamp;
  }

  public int hashCode() {
    return hashCode;
  }

  /*
   * Allows comparing this connection to another
   *
   * @param obj - the other connection to test for equality
   * @see Object#equals(Object)
   */
  public boolean equals(Object obj) {
    if (obj instanceof PooledConnection) {
      return realConnection.hashCode() == (((PooledConnection) obj).realConnection.hashCode());
    } else if (obj instanceof Connection) {
      return hashCode == obj.hashCode();
    } else {
      return false;
    }
  }

  /*
   * Required for InvocationHandler implementation.
   *
   * @param proxy  - not used
   * @param method - the method to be executed
   * @param args   - the parameters to be passed to the method
   * @see java.lang.reflect.InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])
   */
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    if (CLOSE.hashCode() == methodName.hashCode() && CLOSE.equals(methodName)) {
      dataSource.pushConnection(this);
      return null;
    } else {
      try {
        if (!Object.class.equals(method.getDeclaringClass())) {
          // issue #579 toString() should never fail
          // throw an SQLException instead of a Runtime
          checkConnection();
        }
        return method.invoke(realConnection, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
  }

  private void checkConnection() throws SQLException {
    if (!valid) {
      throw new SQLException("Error accessing PooledConnection. Connection is invalid.");
    }
  }

}
