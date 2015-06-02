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
package org.apache.ibatis.datasource.pooled;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * @author Clinton Begin
 */
public class PooledDataSource implements DataSource {

  private static final Log log = LogFactory.getLog(PooledDataSource.class);

  private final PoolState state = new PoolState(this);

  private final UnpooledDataSource dataSource;

  // OPTIONAL CONFIGURATION FIELDS
  //默认的配置参数
  protected int poolMaximumActiveConnections = 10;
  protected int poolMaximumIdleConnections = 5;
  //连接的最长等待时间，在活动链接ActiveConnections达到最大数量的时候，需要查看pooledConnection的第一个活动连接的最后一次使用时间是否超时
  protected int poolMaximumCheckoutTime = 20000;
  protected int poolTimeToWait = 20000;
  //查看数据原连接是否正常
  //查询ping语句
  protected String poolPingQuery = "NO PING QUERY SET";
  protected boolean poolPingEnabled = false;
  protected int poolPingConnectionsNotUsedFor = 0;

  
  private int expectedConnectionTypeCode;

  
  /**
   * 默认构造函数
   * 初始化一个UnpooledDataSource数据源
   */
  public PooledDataSource() {
    dataSource = new UnpooledDataSource();
  }

  /**
   * 构造函数
   * @param driver      驱动
   * @param url           数据库url
   * @param username  用户名
   * @param password  密码
   */
  public PooledDataSource(String driver, String url, String username, String password) {
    //创建一个UnpooledDataSource的数据源
    dataSource = new UnpooledDataSource(driver, url, username, password);
    //获取   "" + url + username + password  的hashcode
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  /**
   * 构造函数
   * 通过properties的方式来获取数据源
   * @param driver                  数据库驱动
   * @param url                       数据库url
   * @param driverProperties   数据库的连接信息（user,password等）
   */
  public PooledDataSource(String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driver, url, driverProperties);
    //获取   "" + url + username + password  的hashcode
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  /**
   * 通过传的classloader来加载数据库驱动
   * @param driverClassLoader   类加载器
   * @param driver                    数据库驱动
   * @param url                         数据库url
   * @param username                数据库用户名
   * @param password                数据库密码
   */
  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
  //获取   "" + url + username + password  的hashcode
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  /**
   * 传递的properties和classloader来初始化数据源
   * @param driverClassLoader
   * @param driver
   * @param url
   * @param driverProperties
   */
  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
  //获取   "" + url + username + password  的hashcode
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  /**
   * 从连接池里边获取一个空闲连接的代理
   */
  public Connection getConnection() throws SQLException {
    return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
  }

  /**
   * 通过制定的用户名和密码从连接池里边获取一个空闲连接的代理
   */
  public Connection getConnection(String username, String password) throws SQLException {
    return popConnection(username, password).getProxyConnection();
  }

  public void setLoginTimeout(int loginTimeout) throws SQLException {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  public int getLoginTimeout() throws SQLException {
    return DriverManager.getLoginTimeout();
  }

  public void setLogWriter(PrintWriter logWriter) throws SQLException {
    DriverManager.setLogWriter(logWriter);
  }

  public PrintWriter getLogWriter() throws SQLException {
    return DriverManager.getLogWriter();
  }

  public void setDriver(String driver) {
    dataSource.setDriver(driver);
    forceCloseAll();
  }

  public void setUrl(String url) {
    dataSource.setUrl(url);
    forceCloseAll();
  }

  public void setUsername(String username) {
    dataSource.setUsername(username);
    forceCloseAll();
  }

  public void setPassword(String password) {
    dataSource.setPassword(password);
    forceCloseAll();
  }

  public void setDefaultAutoCommit(boolean defaultAutoCommit) {
    dataSource.setAutoCommit(defaultAutoCommit);
    forceCloseAll();
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
    forceCloseAll();
  }

  public void setDriverProperties(Properties driverProps) {
    dataSource.setDriverProperties(driverProps);
    forceCloseAll();
  }

  /*
   * The maximum number of active connections
   *
   * @param poolMaximumActiveConnections The maximum number of active connections
   */
  public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
    this.poolMaximumActiveConnections = poolMaximumActiveConnections;
    forceCloseAll();
  }

  /*
   * The maximum number of idle connections
   *
   * @param poolMaximumIdleConnections The maximum number of idle connections
   */
  public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
    this.poolMaximumIdleConnections = poolMaximumIdleConnections;
    forceCloseAll();
  }

  /*
   * The maximum time a connection can be used before it *may* be
   * given away again.
   *
   * @param poolMaximumCheckoutTime The maximum time
   */
  public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
    this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
    forceCloseAll();
  }

  /*
   * The time to wait before retrying to get a connection
   *
   * @param poolTimeToWait The time to wait
   */
  public void setPoolTimeToWait(int poolTimeToWait) {
    this.poolTimeToWait = poolTimeToWait;
    forceCloseAll();
  }

  /*
   * The query to be used to check a connection
   *
   * @param poolPingQuery The query
   */
  public void setPoolPingQuery(String poolPingQuery) {
    this.poolPingQuery = poolPingQuery;
    forceCloseAll();
  }

  /*
   * Determines if the ping query should be used.
   *
   * @param poolPingEnabled True if we need to check a connection before using it
   */
  public void setPoolPingEnabled(boolean poolPingEnabled) {
    this.poolPingEnabled = poolPingEnabled;
    forceCloseAll();
  }

  /*
   * If a connection has not been used in this many milliseconds, ping the
   * database to make sure the connection is still good.
   *设置一个PooledConnection没有被使用的毫秒数限制
   * @param milliseconds the number of milliseconds of inactivity that will trigger a ping
   */
  public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
    this.poolPingConnectionsNotUsedFor = milliseconds;
    //关闭所有的pooledconnection,活动的和空闲的
    forceCloseAll();
  }

  public String getDriver() {
    return dataSource.getDriver();
  }

  public String getUrl() {
    return dataSource.getUrl();
  }

  public String getUsername() {
    return dataSource.getUsername();
  }

  public String getPassword() {
    return dataSource.getPassword();
  }

  public boolean isAutoCommit() {
    return dataSource.isAutoCommit();
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return dataSource.getDefaultTransactionIsolationLevel();
  }

  public Properties getDriverProperties() {
    return dataSource.getDriverProperties();
  }

  public int getPoolMaximumActiveConnections() {
    return poolMaximumActiveConnections;
  }

  public int getPoolMaximumIdleConnections() {
    return poolMaximumIdleConnections;
  }

  public int getPoolMaximumCheckoutTime() {
    return poolMaximumCheckoutTime;
  }

  public int getPoolTimeToWait() {
    return poolTimeToWait;
  }

  public String getPoolPingQuery() {
    return poolPingQuery;
  }

  public boolean isPoolPingEnabled() {
    return poolPingEnabled;
  }

  public int getPoolPingConnectionsNotUsedFor() {
    return poolPingConnectionsNotUsedFor;
  }

  /*
   * Closes all active and idle connections in the pool
   * 关闭所有活动和空闲的连接
   */
  public void forceCloseAll() {
    synchronized (state) {
      //获取hashcode值
      expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
      
      //遍历所有的活动链接数
      for (int i = state.activeConnections.size(); i > 0; i--) {
        try {
          //从活动连接池中移除连接
          PooledConnection conn = state.activeConnections.remove(i - 1);
          //将连接PooledConnection设置成不需要校验的
          conn.invalidate();
          
          //
          Connection realConn = conn.getRealConnection();
          //如果connection不是自动提交事务的，手动回滚
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          //关闭connection
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
      
      //遍历所有的空闲连接PooledConnection，操作和活动的连接一样
      for (int i = state.idleConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.idleConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource forcefully closed/removed all connections.");
    }
  }

  public PoolState getPoolState() {
    return state;
  }

  
  /**
   * 获取   "" + url + username + password  的hashcode
   * @param url             数据库url
   * @param username    用户名
   * @param password    密码
   * @return
   */
  private int assembleConnectionTypeCode(String url, String username, String password) {
    return ("" + url + username + password).hashCode();
  }

  
  protected void pushConnection(PooledConnection conn) throws SQLException {

    synchronized (state) {
      state.activeConnections.remove(conn);
      if (conn.isValid()) {
        if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
          state.idleConnections.add(newConn);
          newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
          newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
          conn.invalidate();
          if (log.isDebugEnabled()) {
            log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
          }
          state.notifyAll();
        } else {
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          conn.getRealConnection().close();
          if (log.isDebugEnabled()) {
            log.debug("Closed connection " + conn.getRealHashCode() + ".");
          }
          conn.invalidate();
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
        }
        state.badConnectionCount++;
      }
    }
  }

  
  /**
   * 获取一个数据源连接
   * @param username    用户名
   * @param password    密码
   * @return
   * @throws SQLException
   */
  private PooledConnection popConnection(String username, String password) throws SQLException {
    boolean countedWait = false;
    PooledConnection conn = null;
    long t = System.currentTimeMillis();
    int localBadConnectionCount = 0;
    
    //如果没有获取到PooledConnection，就一直循环
    while (conn == null) {
      synchronized (state) {
        
        //#####获取一个可用的PooledConnection
          
        //如果空闲的数据源连接大于0
        if (state.idleConnections.size() > 0) {
          // Pool has available connection
          //空闲的连接池里边获取并且移除一个数据库连接
          conn = state.idleConnections.remove(0);
          //打印日志信息,之所以做这个判断，是因为可以提高效率，因为如果不加这个判断，log.debug方法里会运行很多代码才会做这个判断
          //conn.getRealHashCode()因为PooledConnection里边有两个connection，一个是真正的数据库连接，另一个是代理的connection
          if (log.isDebugEnabled()) {
            log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
          }
        } 
        //如果没有空闲的数据库连接
        else {
          // Pool does not have available connection
          //如果活动的连接数小于配置的连接池最大活动连接数
          if (state.activeConnections.size() < poolMaximumActiveConnections) {
            // Can create new connection
            //创建一个新的PooledConnection，并且把当前实例传给PooledConnection
            conn = new PooledConnection(dataSource.getConnection(), this);
            @SuppressWarnings("unused")
            //used in logging, if enabled
            Connection realConn = conn.getRealConnection();
            if (log.isDebugEnabled()) {
              log.debug("Created connection " + conn.getRealHashCode() + ".");
            }
          } else {//如果活动的连接数大于或等于配置的连接池最大活动连接数,无法创建新的数据库连接
            // Cannot create new connection
            //获取被占用时间最长的活动数据库连接
            PooledConnection oldestActiveConnection = state.activeConnections.get(0);
            //获取使用最久的connection  当前时间-最后一次使用时间  查看是否超过设置的最长checkout时间
            long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
            //查看是否超过设置的最长checkout时间
            if (longestCheckoutTime > poolMaximumCheckoutTime) {
              // Can claim overdue connection   可以    断言   过期的  connection
                //超时连接数+1
              state.claimedOverdueConnectionCount++;
              //积累过期的连接超时时间
              state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
              //积累超时时间
              state.accumulatedCheckoutTime += longestCheckoutTime;
              //从activeConnections集合里边移除一个PooledConnection
              state.activeConnections.remove(oldestActiveConnection);
              //如果这个被移除的PooledConnection不是自动提交事务的，就手动回滚事务
              if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                oldestActiveConnection.getRealConnection().rollback();
              }
              //重新初始化一个PooledConnection
              conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
              oldestActiveConnection.invalidate();
              if (log.isDebugEnabled()) {
                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
              }
            } else {
              // Must wait
              try {
                if (!countedWait) {
                  state.hadToWaitCount++;
                  countedWait = true;
                }
                if (log.isDebugEnabled()) {
                  log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                }
                long wt = System.currentTimeMillis();
                state.wait(poolTimeToWait);
                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
              } catch (InterruptedException e) {
                break;
              }
            }
          }
        }
        
        //######获取connection结束
        
        if (conn != null) {
          //验证PooledConnection是否是正常的
          if (conn.isValid()) {
            //回滚事务
            if (!conn.getRealConnection().getAutoCommit()) {
              conn.getRealConnection().rollback();
            }
            //设置code:  "" + url + username + password  的hashcode 值
            conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
            //设置checkouttime和lastusedtime
            conn.setCheckoutTimestamp(System.currentTimeMillis());
            conn.setLastUsedTimestamp(System.currentTimeMillis());
            //将获取到的PooledConnection放到活动的连接池里边
            state.activeConnections.add(conn);
            //
            state.requestCount++;
            //积累请求时间
            state.accumulatedRequestTime += System.currentTimeMillis() - t;
          } else {
            if (log.isDebugEnabled()) {
              log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
            }
            //坏请求+1
            state.badConnectionCount++;
            //本地坏请求+1
            localBadConnectionCount++;
            conn = null;
            //如果本地记录的坏PooledConnection 比（空闲池中最大连接数+3）要多，就抛出运行时异常
            if (localBadConnectionCount > (poolMaximumIdleConnections + 3)) {
              if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Could not get a good connection to the database.");
              }
              throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
            }
          }
        }
      }

    }

    //如果PooledConnection为空，抛出未知的运行时异常
    if (conn == null) {
      if (log.isDebugEnabled()) {
        log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
      }
      throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }

    return conn;
  }

  /*
   * Method to check to see if a connection is still usable
   *校验一个PooledConnection是不是还可以被使用，通过执行一个select语句的方式
   * @param conn - the connection to check
   * @return True if the connection is still usable
   */
  protected boolean pingConnection(PooledConnection conn) {
    boolean result = true;

    try {
      //校验数据库连接是不是被关闭
      result = !conn.getRealConnection().isClosed();
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
      }
      result = false;
    }
    //如果数据库连接没有被关闭
    if (result) {
      //是否允许ping
      if (poolPingEnabled) {
        //判断poolPingConnectionsNotUsedFor（PooledConnection没有使用的时间限制）>=0
        //conn.getTimeElapsedSinceLastUse()  当前时间-最后一次使用的时间
        if (poolPingConnectionsNotUsedFor >= 0 && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
          try {
            if (log.isDebugEnabled()) {
              log.debug("Testing connection " + conn.getRealHashCode() + " ...");
            }
            //获取PooledConnection原生的connection
            Connection realConn = conn.getRealConnection();
            //创建statement
            Statement statement = realConn.createStatement();
            //执行poolPingQuery语句，查看数据是否正常返回
            ResultSet rs = statement.executeQuery(poolPingQuery);
            //关闭数据库
            rs.close();
            statement.close();
            //如果没有设置成自动提交，就回滚
            if (!realConn.getAutoCommit()) {
              realConn.rollback();
            }
            //ping成功，返回true
            result = true;
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
            }
          } catch (Exception e) {
            log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());
            try {
              conn.getRealConnection().close();
            } catch (Exception e2) {
              //ignore
            }
            result = false;
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
            }
          }
        }
      }
    }
    //返回结果
    return result;
  }

  /*
   * Unwraps a pooled connection to get to the 'real' connection
   *
   * @param conn - the pooled connection to unwrap
   * @return The 'real' connection
   */
  public static Connection unwrapConnection(Connection conn) {
    if (Proxy.isProxyClass(conn.getClass())) {
      InvocationHandler handler = Proxy.getInvocationHandler(conn);
      if (handler instanceof PooledConnection) {
        return ((PooledConnection) handler).getRealConnection();
      }
    }
    return conn;
  }

  protected void finalize() throws Throwable {
    forceCloseAll();
  }

  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  public Logger getParentLogger() {
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME); // requires JDK version 1.6
  }

}
