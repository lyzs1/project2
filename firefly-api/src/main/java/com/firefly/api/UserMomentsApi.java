package com.firefly.api;

import com.firefly.api.support.UserSupport;
import com.firefly.domain.JsonResponse;
import com.firefly.domain.UserMoment;
import com.firefly.domain.annotation.ApiLimitedRole;
import com.firefly.domain.annotation.DataLimited;
import com.firefly.domain.constant.AuthRoleConstant;
import com.firefly.service.UserMomentsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


//用户动态
@RestController
public class UserMomentsApi {

    @Autowired
    private UserMomentsService userMomentsService;

    @Autowired
    private UserSupport userSupport;

    //首先要启动rocketmq与redis
    //添加用户动态
    //用户发送动态——>Producer发送message到Topic——>Consumer处理分发逻辑，将动态存入redis
    //使用AOP切面编程进行 接口权限 控制
    //见 ApiLimitedRoleAspect
    @ApiLimitedRole(limitedRoleCodeList = {AuthRoleConstant.ROLE_LV0})
    //使用AOP切面编程进行 数据权限 控制
    //对传参进行权限判断，见 DataLimitedAspect
    @DataLimited
    @PostMapping("/user-moments")
    public JsonResponse<String> addUserMoments(@RequestBody UserMoment userMoment) throws Exception {
        Long userId = userSupport.getCurrentUserId();
        userMoment.setUserId(userId);
        userMomentsService.addUserMoments(userMoment);
        return JsonResponse.success();
    }

    //粉丝查询redis得到动态
    @GetMapping("/user-subscribed-moments")
    public JsonResponse<List<UserMoment>> getUserSubscribedMoments(){
        Long userId = userSupport.getCurrentUserId();
        List<UserMoment> list = userMomentsService.getUserSubscribedMoments(userId);
        return new JsonResponse<>(list);
    }

}
