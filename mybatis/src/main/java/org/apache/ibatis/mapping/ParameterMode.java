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
 * http://blog.sina.com.cn/s/blog_62f0eaa80101bgfc.html
 * 
 * 参数模式
 * 
 * IN参数(PreparedStatement ,  CallableStatement):
 * 
 * 使用PreparedStatement对象不仅可以提高SQL命令的执行效率，还可以向SQL命令传递参数来提高代码的质量。包含于PreparedStatement对象中的SQL语句可具有一个或多个IN参数。IN参数就是可以传入PreparedStatement的SQL语句中的变量。IN参数的值在SQL语句创建时未被指定。相反的，该语句为每个IN参数保留一个问号（“?”）作为占位符。每个问号的值必须通过适当的setXXX方法来提供，其中XXX是与该参数相应的类型，在执行SQL语句时会把问号（“？”）的值传递到SQL语句中。例如，如果参数为Java类型int，则使用的方法就是setInt；如果是String类型的则使用setString。示例代码如下：
	Connection conn = null;
	PreparedStatement pstm = null;
	
	try {
	String URL =  "jdbc:mysql://localhost:3306/bank?username=root&password=root"
	Connection conn =DriverManager.getConnection(URL);
	//添加数据的sql语句
	String sql = "insert into users(id,name,age,tel,address) values(?,?,?,?,?)";
	pstm = conn.prepareStatement(sql);
	pstm.setInt(1, 110);//把添加的id值存入pstm对象中，int类型的值用setInt（）方法
	//把添加的name值存入pstm对象中String类型的值用setString方法
	pstm.setString(2,”MICAL”);
	pstm.setInt(3,25);//把添加的age值存入pstm对象中
	pstm.setString(4,”010-457547654”);//把添加的tel值存入pstm对象中
	pstm.setString(5, “北京市海淀区”);//把添加的address值存入pstm对象中
	
	pstm.executeUpdate();//提交pstm对象
	} catch (Exception e) {
	e.printStackTrace();
	}
	上述代码是一个添加数据的操作，要添加5个字段的值，所以values的括号中有5个问号，在设置IN参数的时候，是按照字段的顺序一一设值的。如果设值的顺序不一样，会导致程序报错，或者不能正确添加数据。


 * OUT参数(CallableStatement):
 * OUT参数是指在存储过程中声明并且赋值的变量。可以使用CallableStatement对象获得OUT参数的值。
	用户如果需要获得储存过程返回的OUT参数，则在执行CallableStatement对象以前必须先注册每个OUT参数的JDBC类型（这是必需的，因为一些数据库管理系统要求存储的数据是JDBC类型）。注册JDBC类型是用registerOutParameter方法来完成的。语句执行完毕后，CallableStatement的getXXX方法将OUT参数值取出。此getXXX方法的作用在于：registerOutParameter使用的是JDBC类型（因此它与数据库返回的JDBC类型匹配），而getXXX将之转换为Java数据类型。下面的代码是OUT参数使用的例子。
	CallableStatement cstmt = conn.prepareCall("{call getSal(?, ?)}");
	//使用registerOutParamete方法注册OUT参数
	cstmt.registerOutParameter(1, java.sql.Types.TINYINT);
	cstmt.registerOutParameter(2, java.sql.Types.DECIMAL, 3);
	//调用存储过程executeQuery方法
	cstmt.executeQuery();
	//使用getByte方法获得OUT参数值，参数类型为字节型
	byte x = cstmt.getByte(1);
	java.math.BigDecimal n = cstmt.getBigDecimal(2, 2);
	上述代码中，先使用CallableStatement对象调用registerOutParameter方法，完成OUT参数的注册，执行由cstmt对象所调用的储存过程，然后检索在OUT参数中返回的值。方法getByte从第一个OUT参数中取出一个Java字节，而getBigDecimal从第二个OUT参数中取出一个BigDecimal对象（小数点后面带两位数）
 *  
 * 
 * 
 * INOUT参数(CallableStatement):
 * 
 * INOUT参数是既支持输入又接受输出的参数，兼具IN参数和OUT参数的特征。使用INOUT参数的时候，除了需要调用registerOutParameter方法之外，还要求调用适当的setXXX方法。setXXX方法将参数值设置为输入参数，而registerOutParameter方法将它的JDBC类型注册为输出参数。setXXX方法提供一个Java值，而驱动程序先把这个值转换为JDBC值，然后将它送到数据库中。这种IN值的JDBC类型和提供给registerOutParameter方法的JDBC类型应该相同。然后，要得到输出值，就要用对应的getXXX方法。例如，Java类型为byte的参数应该使用方法setByte来赋输入值。应该给registerOutParameter提供类型为TINYINT的JDBC类型，同时应使用getByte来获得输出值。
	下例假设已有一个储存过程testInOut，其唯一参数是INOUT参数。方法setByte把此参数设为25，驱动程序将把它作为JDBC TINYINT类型送到数据库中。接着，registerOutParameter将该参数注册为JDBC TINYINT。执行完该已储存过程后，将返回一个新的JDBC TINYINT值。方法getByte将把这个新值作为Java 的byte类型获取。
	CallableStatement cstmt = conn.prepareCall("{call testInOut(?)}");
	cstmt.setByte(1, 100);
	cstmt.registerOutParameter(1, java.sql.Types.TINYINT);
	cstmt.executeUpdate();
	byte a = cstmt.getByte(1);
 * 
 * @author Clinton Begin
 */
public enum ParameterMode {
  IN, OUT, INOUT
}
