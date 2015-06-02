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

import java.util.Collections;
import java.util.Map;

import org.apache.ibatis.session.Configuration;

/**
 * 
 * <discriminator javaType="int" column="draft">
	<case value="1" resultType="DraftPost"/>
	</discriminator>
 * 
 * 有时候一条数据库查询可能会返回包括各种不同的数据类型的结果集。Discriminator（识别器）元素被设计来处理这种情况，
 * 以及其它像类继承层次情况。识别器非常好理解，它就像java里的switch语句。
 
	Discriminator定义要指定column和javaType属性。列是MyBatis将要取出进行比较的值，
	javaType用来确定适当的测试是否正确运行（虽然String在大部分情况下都可以工作）
 * 
 * @author Clinton Begin
 */
public class Discriminator {

  private ResultMapping resultMapping;
  private Map<String, String> discriminatorMap;

  private Discriminator() {
  }

  public static class Builder {
    private Discriminator discriminator = new Discriminator();

    public Builder(Configuration configuration, ResultMapping resultMapping, Map<String, String> discriminatorMap) {
      discriminator.resultMapping = resultMapping;
      discriminator.discriminatorMap = discriminatorMap;
    }

    public Discriminator build() {
      assert discriminator.resultMapping != null;
      assert discriminator.discriminatorMap != null;
      assert discriminator.discriminatorMap.size() > 0;
      //lock down map
      discriminator.discriminatorMap = Collections.unmodifiableMap(discriminator.discriminatorMap);
      return discriminator;
    }
  }

  public ResultMapping getResultMapping() {
    return resultMapping;
  }

  public Map<String, String> getDiscriminatorMap() {
    return discriminatorMap;
  }

  public String getMapIdFor(String s) {
    return discriminatorMap.get(s);
  }

}
