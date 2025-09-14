package com.firefly.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisBloomFilterService {

    private final StringRedisTemplate stringRedisTemplate;

    // 位图大小（bit 数），可在 application.yml 里配置 bloom.video.size
    // 1<<24 ≈ 16,777,216 bits ≈ 2MB；视规模调大
    @Value("${bloom.video.size:16777216}")
    private long bitSize;

    // 多哈希种子（简单、足够好用）
    private static final int[] SEEDS = new int[]{3, 5, 7, 11, 13, 31};

    public RedisBloomFilterService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private long hash(String value, int seed) {
        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            result = result * seed + value.charAt(i);
        }
        // 取正并落到位图长度内
        return (result & 0x7fffffff) % bitSize;
    }

    /** 添加元素到布隆过滤器（设置多个 bit 位为 1） */
    public void put(String bloomKey, String value) {
        for (int seed : SEEDS) {
            long offset = hash(value, seed);
            stringRedisTemplate.opsForValue().setBit(bloomKey, offset, true);
        }
    }

    /** 判断可能存在：所有相关 bit 都为 1 才返回 true；只要有一个为 0 就“确定不存在” */
    public boolean mightContain(String bloomKey, String value) {
        for (int seed : SEEDS) {
            long offset = hash(value, seed);
            Boolean b = stringRedisTemplate.opsForValue().getBit(bloomKey, offset);
            if (b == null || !b) return false; // 确定不存在
        }
        return true; // 可能存在（有误判概率）
    }
}

