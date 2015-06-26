package org.apache.ibatis.cache;

import org.junit.Test;

/**
 * 
 * 类ClassInitTest.java的实现描述：
 * 
 * ####################################
 * 
 * JAVA类的初始化顺序依次是：（静态变量、静态初始化块）->（变量、初始化块）->构造函数，
 * 测试Class<?> c = Class.forName(name, true, cl);中参数initialize的作用
 * 
 * ####################################
 * 
 * @author yuezhihua 2015年5月11日 下午2:49:36
 */
public class ClassInitTest {
    
    /**
     * 调用方法
     * @param args
     */
    @Test
    public void test(){
        try {
            ClassInitTest cit = new ClassInitTest(); 
            
            //等同于Class.forName("org.apache.ibatis.cache.ClassA");
            Class.forName("org.apache.ibatis.cache.ClassA", true, cit.getClass().getClassLoader());
            
            System.out.println("********************************************");
            
            Class.forName("org.apache.ibatis.cache.ClassA", false, cit.getClass().getClassLoader());
            
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    

}
