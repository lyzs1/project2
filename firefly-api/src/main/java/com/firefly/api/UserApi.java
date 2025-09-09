package com.firefly.api;

import com.alibaba.fastjson.JSONObject;
import com.firefly.api.support.UserSupport;
import com.firefly.domain.JsonResponse;
import com.firefly.domain.PageResult;
import com.firefly.domain.User;
import com.firefly.domain.UserInfo;
import com.firefly.service.UserFollowingService;
import com.firefly.service.UserService;
import com.firefly.service.util.RSAUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
public class UserApi {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSupport userSupport;

    @Autowired
    private UserFollowingService userFollowingService;

    @GetMapping("/users")
    public JsonResponse<User> getUserInfo(){
        Long userId = userSupport.getCurrentUserId();
        User user = userService.getUserInfo(userId);
        return new JsonResponse<>(user);
    }

    //获取公钥——RSA加密
    @GetMapping("/rsa-pks")
    public JsonResponse<String> getRsaPulicKey(){
        String pk = RSAUtil.getPublicKeyStr();
        return new JsonResponse<>(pk);
    }

    //注册用户
    @PostMapping("/users")
    public JsonResponse<String> addUser(@RequestBody User user){
        userService.addUser(user);
        return JsonResponse.success();
    }

    /**
     *  单 token 用户登录——>请求用户凭证/令牌/token
     *  password由前端经RSA加密后传入接口
     *  保存到mysql中的password：RSA解码后，经MD5加密
     *  return 实时 token
     *  单token的缺点：user退出登录后，若token在有效期内，仍可访问接口
     *  如果使用单令牌：只需验证 accessToken / token
     *  如果使用双令牌：需验证 accessToken与 RefreshToken
     * @param user
     * @return
     * @throws Exception
     */
    @PostMapping("/user-tokens")
    public JsonResponse<String> login(@RequestBody User user) throws Exception{
        String token = userService.login(user);
        return new JsonResponse<>(token);
    }


    /**
     * 在请求受保护接口时，除了常规参数
     * 应传入Headers的token（键值对）
     * userId 对token解析得到
     * 注意token不要过期
     * @param user
     * @return
     * @throws Exception
     */
    @PutMapping("/users")
    public JsonResponse<String> updateUsers(@RequestBody User user) throws Exception{
        Long userId = userSupport.getCurrentUserId();
        user.setId(userId);
        userService.updateUsers(user);
        return JsonResponse.success();
    }

    @PutMapping("/user-infos")
    public JsonResponse<String> updateUserInfos(@RequestBody UserInfo userInfo){
        Long userId = userSupport.getCurrentUserId();
        userInfo.setUserId(userId);
        userService.updateUserInfos(userInfo);
        return JsonResponse.success();
    }

    //查询用户(模糊查询)
    //返回分页信息
    //传参：第几页、每页大小、昵称
    @GetMapping("/user-infos")
    public JsonResponse<PageResult<UserInfo>> pageListUserInfos(@RequestParam Integer no, @RequestParam Integer size, String nick){
        Long userId = userSupport.getCurrentUserId();
        JSONObject params = new JSONObject();
        params.put("no", no);
        params.put("size", size);
        params.put("nick", nick);
        params.put("userId", userId);
        PageResult<UserInfo> result = userService.pageListUserInfos(params);
       //判断关注状态
        if(result.getTotal() > 0){
            List<UserInfo> checkedUserInfoList = userFollowingService.checkFollowingStatus(result.getList(), userId);
            result.setList(checkedUserInfoList);
        }
        return new JsonResponse<>(result);
    }

    //double-tokens登录
    //return access-token与refresh-token
    @PostMapping("/user-dts")
    public JsonResponse<Map<String, Object>> loginForDts(@RequestBody User user) throws Exception {
        Map<String, Object> map = userService.loginForDts(user);
        //return accessToken 与 refreshToken双令牌
        return new JsonResponse<>(map);
    }

    //用户登出：删除refreshToken
    @DeleteMapping("/refresh-tokens")
    public JsonResponse<String> logout(HttpServletRequest request){
        String refreshToken = request.getHeader("refreshToken");
        Long userId = userSupport.getCurrentUserId();
        userService.logout(refreshToken, userId);
        return JsonResponse.success();
    }
    //前端在获取到状态码“555”后（accessToken过期），调用此接口
    //若refreshToken也过期，告知用户
    //若refreshToken处于有效期，自动刷新accessToken，用户无感访问资源
    @PostMapping("/access-tokens")
    public JsonResponse<String> refreshAccessToken(HttpServletRequest request) throws Exception {
        String refreshToken = request.getHeader("refreshToken");
        String accessToken = userService.refreshAccessToken(refreshToken);
        return new JsonResponse<>(accessToken);
    }
    //双令牌好处：
    //1.用户体验上是长登录：当accessToken过期，但refreshToken有效时
    // 前端自动调用刷新接口，刷新accessToken，用户无感访问资源
    //2.增加了安全性：访问受保护接口时，会同步校验传入的refreshToken
    //与 mysql中对应用户存入的refreshToken是否一致（从accessToken中得到的userid）
}
