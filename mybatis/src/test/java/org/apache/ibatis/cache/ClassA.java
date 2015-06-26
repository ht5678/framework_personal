package org.apache.ibatis.cache;



/**
 * 用来测试的classA
 * 类ClassA.java的实现描述：TODO 类实现描述 
 * @author yuezhihua 2015年5月11日 下午2:58:40
 */
public class ClassA {
    
    public static ClassB classB = new ClassB("init static classB");
    
    static{
        System.out.println("init classA static{} block");
    }
    
    {
        System.out.println("init classA {} block");
    }
    
    public ClassB cb = new ClassB("init variable classB ");
    
    
    public ClassA(){
        System.out.println("init classA");
    }
    
}
