package com.firefly.service.config;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.firefly.domain.UserFollowing;
import com.firefly.domain.UserMoment;
import com.firefly.domain.constant.UserMomentsConstant;
import com.firefly.service.UserFollowingService;
import com.firefly.service.websocket.WebSocketService;
import io.netty.util.internal.StringUtil;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * 订阅发布模式：Producer,Consumer,Broker
 * 观察者模式：Observer，Subject
 * 此处采用模式 1
 *
 *
 * Topic：消息的逻辑分类 “订阅频道”， 解耦producer 与 consumer
 * Topic 划分为多个queue， 实现并发
 * Producer：负责发送消息到 Broker， 发送消息到指定Topic
 * Consumer：从Broker订阅并消费消息， 订阅感兴趣的Topic来接收消息， 可以订阅多个Topic
 * Broker： 消息存储和转发服务器
 *
 *
 * 多线程消费： MessageListenerConcurrently允许多个线程并行处理消息
 * 负载均衡： RocketMQ自动将Topic的队列分配给不同consumer实例与线程
 */
@Configuration
public class RocketMQConfig {

    @Value("${rocketmq.name.server.address}")
    private String nameServerAddr;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserFollowingService userFollowingService;

    @Bean("momentsProducer")
    public DefaultMQProducer momentsProducer() throws Exception{
        DefaultMQProducer producer = new DefaultMQProducer(UserMomentsConstant.GROUP_MOMENTS);
        producer.setNamesrvAddr(nameServerAddr);
        producer.start();
        return producer;
    }


    //Consumer获取信息两种方式：
    // Push:agent推送信息给consumer
    // Pull：consumer从agent按需拉取信息
    //每当有新的messege推送到MQ，consumer通过并发消息监听器进行消息处理
    @Bean("momentsConsumer")
    public DefaultMQPushConsumer momentsConsumer() throws Exception{
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(UserMomentsConstant.GROUP_MOMENTS);
        consumer.setNamesrvAddr(nameServerAddr);
        consumer.subscribe(UserMomentsConstant.TOPIC_MOMENTS, "*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context){
                MessageExt msg = msgs.get(0);
                if(msg == null){
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
                String bodyStr = new String(msg.getBody());
                UserMoment userMoment = JSONObject.toJavaObject(JSONObject.parseObject(bodyStr), UserMoment.class);
                Long userId = userMoment.getUserId();
                List<UserFollowing>fanList = userFollowingService.getUserFans(userId);
                //给所有关注userId的粉丝 push 动态
                //push 动态到 redis
                //redis
                //key 是 不同粉丝
                //value 是整段 JSON 的String（序列化后的 List<UserMoment>）
                for(UserFollowing fan : fanList){
                    String key = "subscribed-" + fan.getUserId();
                    String subscribedListStr = redisTemplate.opsForValue().get(key);
                    List<UserMoment> subscribedList;
                    if(StringUtil.isNullOrEmpty(subscribedListStr)){
                        subscribedList = new ArrayList<>();
                    }else{
                        subscribedList = JSONArray.parseArray(subscribedListStr, UserMoment.class);
                    }
                    subscribedList.add(userMoment);
                    redisTemplate.opsForValue().set(key, JSONObject.toJSONString(subscribedList));
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
        return consumer;
    }

    @Bean("danmusProducer")
    public DefaultMQProducer danmusProducer() throws Exception{
        // 实例化消息生产者Producer
        DefaultMQProducer producer = new DefaultMQProducer(UserMomentsConstant.GROUP_DANMUS);
        // 设置NameServer的地址
        producer.setNamesrvAddr(nameServerAddr);
        // 启动Producer实例
        producer.start();
        return producer;
    }

    @Bean("danmusConsumer")
    public DefaultMQPushConsumer danmusConsumer() throws Exception{
        // 实例化消费者组，内部有一个consumer线程池（并发监听）
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(UserMomentsConstant.GROUP_DANMUS);
        // 设置NameServer的地址
        consumer.setNamesrvAddr(nameServerAddr);
        // 订阅一个或者多个Topic，以及Tag来过滤需要消费的消息,Tag 用 "*" 表示不过滤，来者不拒
        consumer.subscribe(UserMomentsConstant.TOPIC_DANMUS, "*");
        // 注册回调实现类来处理从broker拉取回来的消息
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {

            for (MessageExt msg : msgs) {
                try {
                    JSONObject json = JSONObject.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8));
                    String sessionId = json.getString("sessionId");
                    String message   = json.getString("message");

                    WebSocketService ws = WebSocketService.WEBSOCKET_MAP.get(sessionId);
                    if (ws == null || ws.getSession() == null || !ws.getSession().isOpen()) {
                        // 视需求：重投 or 丢弃
                        // return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                        continue;
                    }
                    ws.sendMessage(message);
                } catch (Exception e) {
                    // 任意一个报错都建议重投整批 or 只重投该条
                    // 这里简单起见重投整批
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        // 启动消费者实例
        consumer.start();
        return consumer;
    }
}
