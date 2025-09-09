package com.firefly.api;

import com.github.tobato.fastdfs.FdfsClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.annotation.PostConstruct;

@Configuration
@Import(FdfsClientConfig.class) // 确保导入 FastDFS 配置类
public class FastdfsConfig {

    @Value("${fdfs.tracker-list}")
    private String trackerList;

    @PostConstruct
    public void init() {
        System.out.println("Tracker List: " + trackerList); // 输出应为 192.168.71.128:22122
    }
}
