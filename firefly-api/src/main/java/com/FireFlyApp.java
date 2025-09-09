package com;

import com.firefly.service.websocket.WebSocketService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Hello world!
 *
 */

@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
public class FireFlyApp
{
    public static void main(String[] args){
        ApplicationContext app = SpringApplication.run(FireFlyApp.class, args);
        WebSocketService.setApplicationContext(app);
    }

}
