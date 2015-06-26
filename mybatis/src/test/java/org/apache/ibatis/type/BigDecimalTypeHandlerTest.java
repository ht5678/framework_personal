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
package org.apache.ibatis.type;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.Test;

public class BigDecimalTypeHandlerTest extends BaseTypeHandlerTest {

  private static final TypeHandler<BigDecimal> TYPE_HANDLER = new BigDecimalTypeHandler();

  
  /**
   * typehandler赋值
   */
  @Test
  public void shouldSetParameter() throws Exception {
    TYPE_HANDLER.setParameter(ps, 1, new BigDecimal(1), null);
    
    /*
     * 单单通过结果来判断正确与否还是不够的，我还要判断是否按我指定的路径执行的用例。
        到这里，终于领略到了mockito的verify的强大威力，以下是示例代码：
        using mocks mockedList.add("one");
         mockedList.add("two"); 
         verify(mockedList).add("one"); 
        若调用成功，则程序正常运行，反之则会报告：Wanted but not invoked:verify(mockedList).add("one"); 
        错误。
     * */
    verify(ps).setBigDecimal(1, new BigDecimal(1));
  }

  
  /**
   * mock模拟获取数据结果
   */
  @Test
  public void shouldGetResultFromResultSet() throws Exception {
    when(rs.getBigDecimal("column")).thenReturn(new BigDecimal(1));
    when(rs.wasNull()).thenReturn(false);
    assertEquals(new BigDecimal(1), TYPE_HANDLER.getResult(rs, "column"));
  }

  @Test
  public void shouldGetResultFromCallableStatement() throws Exception {
    when(cs.getBigDecimal(1)).thenReturn(new BigDecimal(1));
    when(cs.wasNull()).thenReturn(false);
    assertEquals(new BigDecimal(1), TYPE_HANDLER.getResult(cs, 1));
  }

}
