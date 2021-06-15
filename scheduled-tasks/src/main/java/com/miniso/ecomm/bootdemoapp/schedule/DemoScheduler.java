package com.miniso.ecomm.bootdemoapp.schedule;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DemoScheduler {

    @XxlJob("demoScheduler")
    public ReturnT handleXxlJob(String param) {
        return new ReturnT(200, "finished at:" + new Date());
    }
}
