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
package org.apache.ibatis.datasource.unpooled;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.io.Resources;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class UnpooledDataSource implements DataSource {
  
  private ClassLoader driverClassLoader;
  //数据库连接信息properties方式
  private Properties driverProperties;
  //含有系统已经加载的数据库驱动
  //map,key:驱动类的全限定名
  //driver:数据库驱动
  private static Map<String, Driver> registeredDrivers = new ConcurrentHashMap<String, Driver>();
  
  //数据库连接信息
  private String driver;
  private String url;
  private String username;
  private String password;

  //是否自动提交事务
  private Boolean autoCommit;
  //数据库的事务隔离级别
  private Integer defaultTransactionIsolationLevel;

  
  /**
   * 静态构造函数
   */
  static {
    //获取系统加载的所有数据库驱动，如果lib下只有mysql-connector.jar,那就只会加载mysql驱动
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    //如果有驱动加载了，就将驱动信息注册到registeredDrivers的map里边
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      //map,key:驱动类的全限定名
      //driver:数据库驱动
      registeredDrivers.put(driver.getClass().getName(), driver);
    }
  }

  
  /**
   * 默认构造函数
   */
  public UnpooledDataSource() {
  }

  /**
   * 带有数据库连接的构造函数
   * @param driver      数据库驱动，mysql，oracle等
   * @param url           数据库url
   * @param username  数据库用户名
   * @param password  数据库密码
   */
  public UnpooledDataSource(String driver, String url, String username, String password) {
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  /**
   * 带有数据库连接信息的构造函数
   * @param driver                  数据库驱动，mysql，oracle等
   * @param url                       数据库url
   * @param driverProperties   带有数据库信息的property,在获取数据库连接的时候会用到
   *                                          Connection connection = DriverManager.getConnection(url, properties);
   *                                           其中properties至少要含有user和password，就是数据库的用户名和密码
   */
  public UnpooledDataSource(String driver, String url, Properties driverProperties) {
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  /**
   * 带有类加载器和数据库信息的构造函数
   * @param driverClassLoader
   * @param driver
   * @param url
   * @param username
   * @param password
   */
  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  
  /**
   * 带有类加载器和prop资源文件的构造函数
   * @param driverClassLoader
   * @param driver
   * @param url
   * @param driverProperties
   */
  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  
  /**
   * 将数据库的信息（user,password等）组装到properties里边,然后用properties方式获取数据库连接
   */
  public Connection getConnection() throws SQLException {
    return doGetConnection(username, password);
  }

  /**
   * 将数据库的信息（user,password等）组装到properties里边,然后用properties方式获取数据库连接
   */
  public Connection getConnection(String username, String password) throws SQLException {
    return doGetConnection(username, password);
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

  public ClassLoader getDriverClassLoader() {
    return driverClassLoader;
  }

  public void setDriverClassLoader(ClassLoader driverClassLoader) {
    this.driverClassLoader = driverClassLoader;
  }

  public Properties getDriverProperties() {
    return driverProperties;
  }

  public void setDriverProperties(Properties driverProperties) {
    this.driverProperties = driverProperties;
  }

  public String getDriver() {
    return driver;
  }

  public synchronized void setDriver(String driver) {
    this.driver = driver;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Boolean isAutoCommit() {
    return autoCommit;
  }

  public void setAutoCommit(Boolean autoCommit) {
    this.autoCommit = autoCommit;
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return defaultTransactionIsolationLevel;
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    this.defaultTransactionIsolationLevel = defaultTransactionIsolationLevel;
  }

  /**
   * 将数据库的信息（user,password等）组装到properties里边
   * @param username
   * @param password
   * @return
   * @throws SQLException
   */
  private Connection doGetConnection(String username, String password) throws SQLException {
    Properties props = new Properties();
    //driverProperties可能会从构造函数里边传过来
    if (driverProperties != null) {
      props.putAll(driverProperties);
    }
    //数据库的用户名
    if (username != null) {
      props.setProperty("user", username);
    }
    //数据库密码
    if (password != null) {
      props.setProperty("password", password);
    }
    //通过properties方式获取数据库连接connection
    return doGetConnection(props);
  }

  /**
   * 通过properties获取数据库连接connection
   * @param properties  数据库信息(user,password等)
   * @return
   * @throws SQLException
   */
  private Connection doGetConnection(Properties properties) throws SQLException {
    //判断registeredDrivers里边是否注册了当前driver，如果没有注册，就将driver实例化兵器加入到
    initializeDriver();
    //其中properties至少要含有user和password，就是数据库的用户名和密码
    Connection connection = DriverManager.getConnection(url, properties);
    //配置数据库连接是否自动提交和事务隔离级别
    configureConnection(connection);
    return connection;
  }

  
  /**
   * 判断registeredDrivers里边是否注册了当前driver，如果没有注册，就将driver实例化兵器加入到
   * registeredDrivers里边
   * @throws SQLException
   */
  private synchronized void initializeDriver() throws SQLException {
    //如果registeredDrivers中不包含driver
    if (!registeredDrivers.containsKey(driver)) {
      Class<?> driverType;
      try {
        //如果driverClassLoader不为空
        if (driverClassLoader != null) {
          //使用指定的classloader加载，中间的true参数是用来指定是否初始化的
          //初始化值得是在类加载之后，是否会调用类的static静态块和static成员变量的初始化
          //true的时候，除了数组类型的类，其他都会初始化
          driverType = Class.forName(driver, true, driverClassLoader);
        } else {
          //通过类加载器加载数据库驱动
          driverType = Resources.classForName(driver);
        }
        // DriverManager requires the driver to be loaded via the system ClassLoader.
        // http://www.kfu.com/~nsayer/Java/dyn-jdbc.html
        //获取驱动的实例
        Driver driverInstance = (Driver)driverType.newInstance();
        /*
         * 这两种方式是一样的，都是用来注册数据库驱动
         * DriverManager.registerDriver(new Driver());  
         * Class.forName("com.mysql.jdbc.Driver");
         *  那么对于这两种方法，在这里，我推荐使用第二种，即Class.forName("类名")的方式。原因有两点
         *   1、在我们执行DriverManager.registerDriver(new Driver())的时候，静态代码块也已经执行了，相当于是实例化了两个Driver对象。
         *   2、 DriverManager.registerDriver(new Driver())产生了一种对MySQL的一种依赖。而Class.forName的方式我们完全可以在运行的时候再动态改变。
         *   
         * */
        DriverManager.registerDriver(new DriverProxy(driverInstance));
        //将生成的代理driver实例注册到registeredDrivers
        registeredDrivers.put(driver, driverInstance);
      } catch (Exception e) {
        throw new SQLException("Error setting driver on UnpooledDataSource. Cause: " + e);
      }
    }
  }

  
  /**
   * 配置数据库连接是否自动提交和事务隔离级别
   * @param conn
   * @throws SQLException
   */
  private void configureConnection(Connection conn) throws SQLException {
    if (autoCommit != null && autoCommit != conn.getAutoCommit()) {
      conn.setAutoCommit(autoCommit);
    }
    if (defaultTransactionIsolationLevel != null) {
      conn.setTransactionIsolation(defaultTransactionIsolationLevel);
    }
  }

  
  /**
   * Driver扩展
   * 实际是对driver的代理类，对它的一些方法进行了装饰
   * 类UnpooledDataSource.java的实现描述：TODO 类实现描述 
   * @author yuezhihua 2015年5月13日 下午2:17:37
   */
  private static class DriverProxy implements Driver {
    private Driver driver;

    DriverProxy(Driver d) {
      this.driver = d;
    }

    public boolean acceptsURL(String u) throws SQLException {
      return this.driver.acceptsURL(u);
    }

    public Connection connect(String u, Properties p) throws SQLException {
      return this.driver.connect(u, p);
    }

    public int getMajorVersion() {
      return this.driver.getMajorVersion();
    }

    public int getMinorVersion() {
      return this.driver.getMinorVersion();
    }

    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
      return this.driver.getPropertyInfo(u, p);
    }

    public boolean jdbcCompliant() {
      return this.driver.jdbcCompliant();
    }

    public Logger getParentLogger() {
      return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
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
