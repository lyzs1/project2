package com.firefly.service.util;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocketMQUtil {


    //同步发送
    public static void syncSendMsg(DefaultMQProducer producer, Message msg) throws Exception{
        SendResult result = producer.send(msg);
        System.out.println(result);
    }


    //异步发送
    public static void asyncSendMsg(DefaultMQProducer producer, Message msg) throws Exception{
        //通过回调机制实现非阻塞发送
        // 支持高并发：批量消息发送，适用于突发流量场景
        producer.send(msg, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                Logger logger = LoggerFactory.getLogger(RocketMQUtil.class);
                logger.info("异步发送消息成功，消息id：" + sendResult.getMsgId());
            }
            @Override
            public void onException(Throwable e) {
                e.printStackTrace();
            }
        });
    }
}
