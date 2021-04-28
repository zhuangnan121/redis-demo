package com.ebanma.cloud.redis.controller;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: nan.zhuang
 * @Date: 2021/04/27/17:07
 * @Description:做一个超卖
 */

@RestController
@Slf4j
public class TestController {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private Redisson redisson;

    @RequestMapping("/redis")
    public String redis(){
        String key = "product1";
        long releaseTime = 30 * 1000;
        String value = UUID.randomUUID().toString();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, value, releaseTime, TimeUnit.MILLISECONDS);
        try {
            if(result){
                // 改库存
                changeStock();
                return "抢购成功";
            }else{
                long newTime = System.currentTimeMillis();
                long loseTime = newTime + releaseTime;
                while(System.currentTimeMillis() < loseTime){
                    Boolean retryResult = stringRedisTemplate.opsForValue().setIfAbsent(key, value, releaseTime, TimeUnit.MILLISECONDS);
                    if(retryResult){
                        changeStock();
                        return "抢购成功";
                    }else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
                return "系统繁忙";
            }
        } finally {
            if (stringRedisTemplate.opsForValue().get(key).equals(value)) {
                // 风险是如果判断完后过期了，可能会删除别的锁
                stringRedisTemplate.delete(key);
            }

        }
    }

    public void changeStock() {
        Integer stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("stock"));
        if(stock > 0 ){
            stock --;
            stringRedisTemplate.opsForValue().set("stock", stock.toString());
        }
    }

    @RequestMapping("/redisson")
    public String redisson() {
        String key = "product1";
        RLock lock = redisson.getLock(key);
        try {
            lock.lock();
            Integer stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("stock"));
            if (stock > 0){
                stock --;
                stringRedisTemplate.opsForValue().set("stock", stock.toString());
                return "扣减成功";
            }else {
                return "商品售完，扣减失败";
            }
        } finally {
            lock.unlock();
        }
    }
}
