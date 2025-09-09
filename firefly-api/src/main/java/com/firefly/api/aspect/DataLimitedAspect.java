package com.firefly.api.aspect;

import com.firefly.api.support.UserSupport;
import com.firefly.domain.UserMoment;
import com.firefly.domain.auth.UserRole;
import com.firefly.domain.constant.AuthRoleConstant;
import com.firefly.domain.exception.ConditionException;
import com.firefly.service.UserRoleService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Order(1)
@Component
@Aspect
public class DataLimitedAspect {

    @Autowired
    private UserSupport userSupport;

    @Autowired
    private UserRoleService userRoleService;

    @Pointcut("@annotation(com.firefly.domain.annotation.DataLimited)")
    public void check(){
    }

    //通过在对应接口上声明@DataLimited切点注解，校验入参的数据限制（如：LV1只能发“type = 0”的视频动态）
    //在目标方法执行前拿到注解参数 limitedRoleCodeList
    @Before("check()")
    public void doBefore(JoinPoint joinPoint){
        Long userId = userSupport.getCurrentUserId();
        List<UserRole> userRoleList = userRoleService.getUserRoleByUserId(userId);
        Set<String> roleCodeSet = userRoleList.stream().map(UserRole::getRoleCode).collect(Collectors.toSet());
        Object[] args = joinPoint.getArgs();
        for(Object arg : args){
          if(arg instanceof UserMoment){
              UserMoment userMoment = (UserMoment)arg;
              String type = userMoment.getType();
              if(roleCodeSet.contains(AuthRoleConstant.ROLE_LV1) && !"0".equals(type)){
                  throw new ConditionException("参数异常");
              }
          }
        }
    }
}
