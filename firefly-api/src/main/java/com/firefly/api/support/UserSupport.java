package com.firefly.api.support;

import com.firefly.domain.exception.ConditionException;
import com.firefly.service.UserService;
import com.firefly.service.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Component
public class UserSupport {

    @Autowired
    private UserService userService;

    /**
     *如果使用单令牌：只需验证 accessToken / token
     * 如果使用双令牌：需验证 accessToken与 RefreshToken
     * @return
     */
    public Long getCurrentUserId() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = requestAttributes.getRequest();
        String token = request.getHeader("token");
        Long userId = TokenUtil.verifyToken(token);
        //直接解析accessToken得到userid，而不是像session那样 将userid存在内存
        if(userId < 0) {
            throw new ConditionException("非法用户");
        }
        this.verifyRefreshToken(userId);
        return userId;
    }

    //验证刷新令牌RefreshToken
    private void verifyRefreshToken(Long userId){
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        String refreshToken = requestAttributes.getRequest().getHeader("refreshToken");
        String dbRefreshToken = userService.getRefreshTokenByUserId(userId);
        if(!dbRefreshToken.equals(refreshToken)){
            throw new ConditionException("非法用户！");
        }
    }


}
