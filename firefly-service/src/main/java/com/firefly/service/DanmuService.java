package com.firefly.service;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.firefly.dao.DanmuDao;
import com.firefly.domain.Danmu;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DanmuService {

    private static final String DANMU_KEY = "dm-video-";
    private static final String BLOOM_KEY = "bf:video:exists";
    private static final String NULL_KEY_PREFIX = "dm-null-";

    @Autowired
    private DanmuDao danmuDao;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void addDanmu(Danmu danmu){
        danmuDao.addDanmu(danmu);
    }

    @Autowired
    private RedisBloomFilterService bloom;

    //添加danmu到数据库
    @Async
    public void asyncAddDanmu(Danmu danmu){
        danmuDao.addDanmu(danmu);
    }

    /**
     * 查询策略是优先查redis中的弹幕数据，
     * 如果没有的话查询数据库，然后把查询的数据写入redis当中
     * redis作为旁路缓存（Cache-Aside），采用 读写缓存， 实现了缓存缺失时的处理逻辑
     */
    // ========= 读：先空值缓存 -> Bloom 预判 -> 原有旁路缓存 =========
    public List<Danmu> getDanmus(Long videoId,
                                 String startTime, String endTime) throws Exception {
        String key = DANMU_KEY + videoId;
        String nullKey = NULL_KEY_PREFIX + videoId;

        // 0) 空值短期缓存：命中直接返回空，防止击穿
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullKey))) {
            return Collections.emptyList();
        }

        // 1) Bloom 预判：若“确定不存在”，直接返回空（再加一份短期空值缓存）
        if (!bloom.mightContain(BLOOM_KEY, String.valueOf(videoId))) {
            // 不存在的 videoId 或从未见过的冷 ID
            redisTemplate.opsForValue().set(nullKey, "1", 2, TimeUnit.MINUTES);
            return Collections.emptyList();
        }

        // 2) 原有：先查缓存
        String value = redisTemplate.opsForValue().get(key);
        List<Danmu> list;
        if (value != null && !value.isEmpty()) {
            list = JSONArray.parseArray(value, Danmu.class);
            // 时间过滤（保持你原有逻辑）
            if (startTime != null && !startTime.isEmpty()
                    && endTime != null && !endTime.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date startDate = sdf.parse(startTime);
                Date endDate = sdf.parse(endTime);
                List<Danmu> childList = new ArrayList<>();
                for (Danmu danmu : list) {
                    Date t = danmu.getCreateTime();
                    if (t.after(startDate) && t.before(endDate)) childList.add(danmu);
                }
                list = childList;
            }
            return list;
        }

        // 3) 未命中缓存 -> 查库
        Map<String, Object> params = new HashMap<>();
        params.put("videoId", videoId);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        list = danmuDao.getDanmus(params);

        // 4) 回填缓存 + 维护 Bloom/空值缓存
        if (list != null && !list.isEmpty()) {
            // 有弹幕：正常回填缓存，并把 videoId 放进布隆（幂等）
            redisTemplate.opsForValue().set(key, JSONObject.toJSONString(list), 10, TimeUnit.MINUTES);
            bloom.put(BLOOM_KEY, String.valueOf(videoId));
            // 若之前有空值标记，清掉
            redisTemplate.delete(nullKey);
        } else {
            // 无弹幕：也要“回填空值缓存”（防击穿），给较短 TTL
            redisTemplate.opsForValue().set(key, "[]", 2, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(nullKey, "1", 2, TimeUnit.MINUTES);
            // 不往 Bloom 里写（Bloom 用来表征“可能存在”，由视频创建或首次命中时写入）
        }
        return list == null ? Collections.emptyList() : list;
    }

    //添加danmu到redis
    //key：videoId
    //value：List<Danmu>
    // ========= 写：追加弹幕时，正常更新缓存 + 维护 Bloom/空值缓存 =========
    public void addDanmusToRedis(Danmu danmu) {
        String key = DANMU_KEY + danmu.getVideoId();
        String value = redisTemplate.opsForValue().get(key);
        List<Danmu> list = new ArrayList<>();
        if (value != null && !value.isEmpty()) {
            list = JSONArray.parseArray(value, Danmu.class);
        }
        list.add(danmu);
        // 正常回写缓存，并续期（你原本就是 10 分钟）
        redisTemplate.opsForValue().set(key, JSONObject.toJSONString(list), 10, TimeUnit.MINUTES);

        // 新增弹幕意味着该视频“确实存在”，把 videoId 加入 Bloom，并清理空值标记
        bloom.put(BLOOM_KEY, String.valueOf(danmu.getVideoId()));
        redisTemplate.delete(NULL_KEY_PREFIX + danmu.getVideoId());
    }

}
