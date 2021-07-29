package com.miniso.ecomm.bootdemoapp;

import com.alibaba.fastjson.JSON;
import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.bootdemoapp.client.dto.SomeDummyObjDTO;
import com.miniso.ecomm.bootdemoapp.client.service.DemoDubboService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

@SpringBootTest(classes = BootDemoAppApplication.class)
@Slf4j
class BootDemoAppApplicationTest {

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
}
