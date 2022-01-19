package com.miniso.ecomm.bootdemoapp;

import com.miniso.ecomm.apigateway.client.services.lazada.LazadaOrderService;
import com.miniso.ecomm.apigateway.client.services.tokopedia.TokopediaOrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = BootDemoAppApplication.class)
@Slf4j
public class OrderLevelDataTempFetchTest {

    @Autowired
    private LazadaOrderService lazadaOrderService;

    @Autowired
    private TokopediaOrderService tokopediaOrderService;

    @Test
    void contextLoads() {
    }
}
