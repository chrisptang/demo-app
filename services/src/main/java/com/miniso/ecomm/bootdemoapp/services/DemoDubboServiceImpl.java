package com.miniso.ecomm.bootdemoapp.services;

import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.bootdemoapp.client.dto.SomeDummyObjDTO;
import com.miniso.ecomm.bootdemoapp.client.service.DemoDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Date;

@DubboService
public class DemoDubboServiceImpl implements DemoDubboService {

    @DubboReference
    private DemoDubboService demoDubboService;

    @Override
    public Result<String> anyService(SomeDummyObjDTO someDummyObjDTO) {
        if (someDummyObjDTO == null || someDummyObjDTO.getAnotherKey() == -1) {
            return Result.failed("You choose to return failed result:" + new Date());
        }
        return Result.success(new Date() + "\nYour input:" + someDummyObjDTO);
    }
}
