package com.firefly.api.aspect;

import com.firefly.api.support.UserSupport;
import com.firefly.domain.annotation.ApiLimitedRole;
import com.firefly.domain.auth.UserRole;
import com.firefly.domain.exception.ConditionException;
import com.firefly.service.UserRoleService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Order(1)
@Component
@Aspect
public class ApiLimitedRoleAspect {

    @Autowired
    private UserSupport userSupport;

    @Autowired
    private UserRoleService userRoleService;

    @Pointcut("@annotation(com.firefly.domain.annotation.ApiLimitedRole)")
    public void check(){
    }

    //通过在对应接口上声明@ApiLimitedRole切点注解，限制某些role调用接口（如：LV0禁止发动态）
    //在目标方法执行前拿到注解参数 limitedRoleCodeList
    @Before("check() && @annotation(apiLimitedRole)")
    public void doBefore(JoinPoint joinPoint, ApiLimitedRole apiLimitedRole){
        Long userId = userSupport.getCurrentUserId();
        List<UserRole> userRoleList = userRoleService.getUserRoleByUserId(userId);
        String[] limitedRoleCodeList = apiLimitedRole.limitedRoleCodeList();
        Set<String> limitedRoleCodeSet = Arrays.stream(limitedRoleCodeList).collect(Collectors.toSet());
        Set<String> roleCodeSet = userRoleList.stream().map(UserRole::getRoleCode).collect(Collectors.toSet());
        //将用户的所有角色与被限制接入接口的所有角色取交集
        roleCodeSet.retainAll(limitedRoleCodeSet);
        //若有交集，则此用户不可访问接口
        if(roleCodeSet.size() > 0){
            throw new ConditionException("权限不足！");
        }
    }
}
