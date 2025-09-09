package com.firefly.service.websocket;

import com.alibaba.fastjson.JSONObject;
import com.firefly.domain.Danmu;
import com.firefly.domain.constant.UserMomentsConstant;
import com.firefly.service.DanmuService;
import com.firefly.service.util.RocketMQUtil;
import com.firefly.service.util.TokenUtil;
import io.netty.util.internal.StringUtil;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
//WebSocket的端点类，暴露 WebSocket 接入地址，多例模式
//WebSocket协议：全双工
//作用：当向此路径发起握手后，容器会为每个链接创建一个该类的实例
//并触发回调 @OnOpen / @OnMessage / @OnClose / @OnError 方法，实现长连接、全双工的弹幕收发
//连接建立后，HTTP 协议升级为 WS，全双工、长连接
@ServerEndpoint("/imserver/{token}")
public class WebSocketService {

    private final Logger logger =  LoggerFactory.getLogger(this.getClass());


    //当前总连接客户端数量/总在线人数，AtomicInteger 线程安全
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);

    //用 ConcurrentHashMap 管理所有在线会话，保存每个客户端的单独的WebSocketService
    public static final ConcurrentHashMap<String, WebSocketService> WEBSOCKET_MAP = new ConcurrentHashMap<>();

    private Session session;

    private String sessionId;

    //发送danmu的userId
    private Long userId;

    //通过ApplicationContext获取需要的bean实例
    //在ImoocBilibili.App启动类中找到ApplicationContext
    //static 全局共用
    //解决：多例模式不能使用autowired等注解自动注入
    private static ApplicationContext APPLICATION_CONTEXT;

    public static void setApplicationContext(ApplicationContext applicationContext){
        WebSocketService.APPLICATION_CONTEXT = applicationContext;
    }

    //客户端发起WebSocket握手请求 ——> 建立长连接
    @OnOpen
    public void openConnection(Session session, @PathParam("token") String token){
        try{
            //通过token获取userId
            //游客模式下，没有userId也可以
            this.userId = TokenUtil.verifyToken(token);
        }catch (Exception ignored){}
        this.sessionId = session.getId();
        this.session = session;
        //判断并更新 WEBSOCKET_MAP
        if(WEBSOCKET_MAP.containsKey(sessionId)){
            //如果已经链接，更新session
            WEBSOCKET_MAP.remove(sessionId);
            WEBSOCKET_MAP.put(sessionId, this);
        }else{
            //如果客户端第一次链接
            WEBSOCKET_MAP.put(sessionId, this);
            ONLINE_COUNT.getAndIncrement();
        }
        logger.info("用户连接成功：" + sessionId + "，当前在线人数为：" + ONLINE_COUNT.get());
        try{
            this.sendMessage("0");
        }catch (Exception e){
            logger.error("连接异常");
        }
    }

    //关闭链接
    @OnClose
    public void closeConnection(){
        if(WEBSOCKET_MAP.containsKey(sessionId)){
            WEBSOCKET_MAP.remove(sessionId);
            ONLINE_COUNT.getAndDecrement();
        }
        logger.info("用户退出：" + sessionId + "当前在线人数为：" + ONLINE_COUNT.get());
    }

    //客户端发送danmu调用此方法
    @OnMessage
    public void onMessage(String message){
        logger.info("用户信息：" + sessionId + "，报文：" + message);
        if(!StringUtil.isNullOrEmpty(message)){
            try{
                //遍历所有 WebSocket链接， 建立n个message{“sessionId”, “danmu”}，全部发送到MQ,n为当前在线连接数
                for(Map.Entry<String, WebSocketService> entry : WEBSOCKET_MAP.entrySet()){
                    WebSocketService webSocketService = entry.getValue();
                    // danmu群发优化： MQ削峰 + 并发
                    // 构建Producer传递message到RocketMQ
                    // 由danmusConsumer进行webSocketService.sendMessage(message)
                    // 用于群发突发的danmu流量
                    DefaultMQProducer danmusProducer = (DefaultMQProducer)APPLICATION_CONTEXT.getBean("danmusProducer");
                    //构建消息内容
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("message", message);
                    jsonObject.put("sessionId", webSocketService.getSessionId());
                    //指定Topic
                    Message msg = new Message(UserMomentsConstant.TOPIC_DANMUS, jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));
                    //异步并发消息
                    RocketMQUtil.asyncSendMsg(danmusProducer, msg);
                }

                if(this.userId != null){
                    //保存弹幕到数据库
                    Danmu danmu = JSONObject.parseObject(message, Danmu.class);
                    danmu.setUserId(userId);
                    danmu.setCreateTime(new Date());
                    DanmuService danmuService = (DanmuService)APPLICATION_CONTEXT.getBean("danmuService");
                    //danmu数据库存储优化
                    // 1.MQ削峰， 代码中未实现，可以添加
                    // 2. 异步存储(@Async注解)
                    danmuService.asyncAddDanmu(danmu);
                    //保存弹幕到redis
                    //保证快速返回一定时间范围内的所有danmu
                    //此处是同步存储
                    danmuService.addDanmusToRedis(danmu);
                }
            }catch (Exception e){
                logger.error("弹幕接收出现问题");
                e.printStackTrace();
            }
        }
    }

    @OnError
    public void onError(Throwable error){
    }

    //服务器 主动 ——> 客户端
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }

    //定时返回在线人数（服务器 ——> 客户端）
    //或直接指定时间间隔，例如：5秒
    @Scheduled(fixedRate=5000)
    private void noticeOnlineCount() throws IOException {
        for(Map.Entry<String, WebSocketService> entry : WebSocketService.WEBSOCKET_MAP.entrySet()){
            WebSocketService webSocketService = entry.getValue();
            if(webSocketService.session.isOpen()){
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("onlineCount", ONLINE_COUNT.get());
                jsonObject.put("msg", "当前在线人数为" + ONLINE_COUNT.get());
                webSocketService.sendMessage(jsonObject.toJSONString());
            }
        }
    }

    public Session getSession() {
        return session;
    }

    public String getSessionId() {
        return sessionId;
    }
}
