package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIDWorker redisIDWorker;
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void test() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    public void idGentest() throws InterruptedException {
//        long test = redisIDWorker.getUniqueId("test");
//        System.out.println(test);
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()->{
          for (int i = 0; i < 100; i++) {
              long id = redisIDWorker.getUniqueId("test");
              System.out.println("id: " + id);
          }
          latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("end-time: " + (end - start));
    }
}
