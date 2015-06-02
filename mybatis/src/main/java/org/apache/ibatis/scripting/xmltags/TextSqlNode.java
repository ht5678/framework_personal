/*
 *    Copyright 2009-2013 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * @author Clinton Begin
 */
public class TextSqlNode implements SqlNode {
  
  //带有变量#{xx}的sql语句
  private String text;

  
  /**
   * 构造函数
   * @param text        带有变量#{xx}的sql语句
   */
  public TextSqlNode(String text) {
    this.text = text;
  }
  
  /**
   * 判断一个sql语句文本是否包含  ${xx}  的变量
   * @return
   */
  public boolean isDynamic() {
    DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
    GenericTokenParser parser = createParser(checker);
    parser.parse(text);
    return checker.isDynamic();
  }

  public boolean apply(DynamicContext context) {
    GenericTokenParser parser = createParser(new BindingTokenParser(context));
    context.appendSql(parser.parse(text));
    return true;
  }
  
  
  /**
   * 新增一个起止符为${ },处理类为TokenHandler的子类的GenericTokenParser
   * 会在GenericTokenParser的parse方法里边解析sql语句的${xx}变量
   * @param handler
   * @return
   */
  private GenericTokenParser createParser(TokenHandler handler) {
    return new GenericTokenParser("${", "}", handler);
  }

  
  private static class BindingTokenParser implements TokenHandler {

    private DynamicContext context;

    public BindingTokenParser(DynamicContext context) {
      this.context = context;
    }

    
    public String handleToken(String content) {
      Object parameter = context.getBindings().get("_parameter");
      if (parameter == null) {
        context.getBindings().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
        context.getBindings().put("value", parameter);
      }
      Object value = OgnlCache.getValue(content, context.getBindings());
      return (value == null ? "" : String.valueOf(value)); // issue #274 return "" instead of "null"
    }
  }

  
  /**
   * 
   * 判断GenericTokenParser的parse方法是否会调用里边的handleToken方法，如果调用，说明是有${xx}的动态sql语句
   * @author yuezhihua 2015年5月18日 下午3:28:12
   */
  private static class DynamicCheckerTokenParser implements TokenHandler {
    
    private boolean isDynamic;

    public boolean isDynamic() {
      return isDynamic;
    }

    /**
     * 如果调用了解析${xx}的parse方法，就判断为dynamic的sql语句
     */
    public String handleToken(String content) {
      this.isDynamic = true;
      return null;
    }
  }
  
}