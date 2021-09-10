package com.miniso.ecomm.bootdemoapp;

import com.alibaba.fastjson.JSON;
import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.bootdemoapp.client.dto.SomeDummyObjDTO;
import com.miniso.ecomm.bootdemoapp.client.service.DemoDubboService;
import com.miniso.ecomm.bootdemoapp.schedule.FetchFinanceItemsTask;
import com.miniso.ecomm.bootdemoapp.schedule.FetchOrderItemsTask;
import com.xxl.job.core.biz.model.ReturnT;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = BootDemoAppApplication.class)
@Slf4j
class BootDemoAppApplicationTest {

    @Autowired
    private FetchOrderItemsTask fetchOrderItemsTask;

    @Autowired
    private FetchFinanceItemsTask fetchFinanceItemsTask;

    @Test
    void contextLoads() {
    }

    @Autowired
    private DemoDubboService demoDubboService;

    @Test
    public void testDubboService() {
        SomeDummyObjDTO objDTO = new SomeDummyObjDTO()
                .setAnotherKey(100)
                .setSomeKey(new Date().toString());
        Result<String> result = demoDubboService.anyService(objDTO);
        log.info(JSON.toJSONString(result, true));
        Assert.assertTrue(Result.isSuccess(result));
    }

    @Test
    public void testFetchOrderItemsTask() throws InterruptedException, ParseException {
        String[] ranges = {"2021-02-18:2021-02-20", "2021-01-24:2021-01-26", "2021-01-06:2021-01-08", "2020-12-17:2020-12-19"
                , "2020-11-28:2020-11-30", "2020-10-18:2020-10-20", "2020-09-25:2020-09-27", "2020-09-18:2020-09-20"
                , "2020-08-27:2020-08-29", "2020-06-25:2020-06-27", "2020-06-19:2020-06-21"};

        for (String dateRange : ranges) {
            fetchOrderItemsTask.fetchTokopedia(dateRange);
        }
        TimeUnit.MINUTES.sleep(45L);
        Assert.assertTrue(true);
    }

    @Test
    public void testFetchFinanceItemsTask() throws InterruptedException {
        ReturnT<String> returnT = fetchFinanceItemsTask.fetchTokopedia("2021-06-01:2021-07-01");
        log.info(JSON.toJSONString(returnT));
        Assert.assertTrue(ReturnT.SUCCESS_CODE == returnT.getCode());
        TimeUnit.MINUTES.sleep(45L);
    }

    @Test
    public void testFetchAmazonOrderItemsTask() throws InterruptedException {
        ReturnT<String> returnT = fetchOrderItemsTask.fetchAmazon("2021-07-13:2021-07-26");
        log.info(JSON.toJSONString(returnT));
        Assert.assertTrue(ReturnT.SUCCESS_CODE == returnT.getCode());
        TimeUnit.MINUTES.sleep(5L);
    }
}
