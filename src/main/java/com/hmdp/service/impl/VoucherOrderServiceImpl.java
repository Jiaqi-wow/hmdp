package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIDWorker redisIDWorker;

    public Result seckillOrder(Long voucherId) {
        /*
            秒杀下单的业务逻辑：
            1. 根据id查询秒杀券的记录
            2. 判断当前时间是否处于开售中，如果不属于则返回错误信息，如果属于则继续执行
            3. 判断库存是否大于零，如果不大于零则返回错误信息，如果大于零则下单成功
            4. 修改秒杀券表的库存
         */

        /*
            高并发情况下，该代码存在线程安全问题：查询数据库和更新数据库之间可能会被插入新的线程，从而使查询到的数据失去真实性，最终出现超卖情况。
            使用乐观锁的方式保证线程安全：更新数据库时确保此时库存确实大于0。
         */

        /*
            一人一单：
            想要实现一人一单的问题，首先需要查看数据库中是否已经存在该用户买了该券的数据，如果已经有了就不可以在买了，
            此时，高并发时有会出现问题，因为查询数据库和向数据库中写入订单这两个数据库操作直接会别其他线程插入。

            解决并发安全问题：
            无法使用乐观锁了，因为乐观锁是在更新前查询，而一人一单是新增操作，没有可查询的数据。
            使用悲观锁，思考：用什么作为锁？用用户的id，相同用户多线程并发下单，使用悲观锁将其变为串行。
            思考：给哪部分代码加锁？查询订单数据库+更新库存+新增订单这部分代码加锁
            思考：锁与事务的关系？锁一定要包含事务，在事务提交之后再释放锁

            synchronized 加到方法上 锁是this，是给对象加锁，而该对象是唯一的，那么每个线程会变成串行执行；

            事务失效问题：成员方法A调用另一个成员方法B，成员方法B如果加了事务，这个事务会失效，因为A调B是this
            调用，而真正有事务功能的是动态代理类对象的B，而不是this的B
         */
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        if(LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())){
            return Result.fail("未开售");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("已停售");
        }

        if (seckillVoucher.getStock() < 1){
            return Result.fail("已售罄");
        }

        synchronized (UserHolder.getUser().getId().toString().intern()){
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        int count = query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", voucherId).count();
        if (count > 0){
            return Result.fail("不可重复购买");
        }

        boolean update = seckillVoucherService.update()
                .setSql("stock=stock-1").eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!update){
            return Result.fail("已售罄");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(redisIDWorker.getUniqueId("secKillOrder"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);

        return Result.ok();
    }
}
