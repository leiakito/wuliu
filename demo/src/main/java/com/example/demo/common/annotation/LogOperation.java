package com.example.demo.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) //限定只能用在方法上
@Retention(RetentionPolicy.RUNTIME) //在运行时依然有效，可以被反射读取
//定义一个自定义注解，名为 LogOperation  注解的参数，用来描述操作内容（比如“删除用户”）
public @interface LogOperation {
    String value();
}
