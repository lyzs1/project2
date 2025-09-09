package com.firefly.service.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.firefly.domain.exception.ConditionException;

import java.util.Calendar;
import java.util.Date;

/**
 * JWT 基础概念详解 ： https://javaguide.cn/system-design/security/jwt-intro.html
 * 基于JWT的token用户登录验证：
 * 服务器通过 Payload、Header 和 Secret(密钥)创建 JWT 并将 JWT 发送给客户端。
 * 客户端接收到 JWT 之后，会将其保存 localStorage，
 * 以后客户端发出的所有请求都会携带这个令牌
 *
 *相对于 Cookie + Session的优势：
 * token自身包含了身份验证的所有用户信息（userid）
 * 不用像session存储在内存中，也就不会有问题1、2
 * 1.服务器重启不丢用户信息
 * 2.不占内存、用户访问量增大不会挤爆 JVM内存
 * 3. 减少了token被窃取的风险，因为客户端将token存储在localstorage，不是cookie
 *
 */
public class TokenUtil {

    private static final String ISSUER = "签发者";


    //userId——>token
    //JWT（Json Web Token）的规范化生成
    // token组成：头部、载荷、秘钥组合加密
    public static String generateToken(Long userId) throws Exception{

        Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.HOUR, 1);
        return JWT.create()
                .withKeyId(String.valueOf(userId))
                // (1) 把“kid”放到JWT头部（这里不太合理）
                // 应该把uid放到payload部分： sub ：主题
                .withIssuer(ISSUER)                  // (2) payload部分：iss：签发方
                .withExpiresAt(calendar.getTime())   // (3) payload部分：exp：过期时间=现在时刻+ 1h
                .sign(algorithm);
                // (4) 用私钥对header与payload加密生成签名sign（使用RS256算法）
                //sign用于防止token被篡改，因为私钥没有泄露
    }


    public static String generateRefreshToken(Long userId) throws Exception{
        Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        return JWT.create()
                .withKeyId(String.valueOf(userId))
                .withIssuer(ISSUER)
                .withExpiresAt(calendar.getTime())
                .sign(algorithm);

    }

    //验证、解析令牌——>userId
    public static Long verifyToken(String token) {
        try {
            Algorithm algorithm = Algorithm.RSA256(RSAUtil.getPublicKey(), RSAUtil.getPrivateKey());
            JWTVerifier verifier = JWT.require(algorithm).build();
            //此处自动验证是否过期
            DecodedJWT jwt = verifier.verify(token);
            String userId = jwt.getKeyId();
            return Long.valueOf(userId);
        } catch (TokenExpiredException e) {
            throw new ConditionException("555", "token过期！");
        } catch (Exception e) {
            throw new ConditionException("非法用户token！");
        }


    }


}
