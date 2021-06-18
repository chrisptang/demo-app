package com.miniso.ecomm.bootdemoapp.services;

import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.bootdemoapp.client.dto.SomeDummyObjDTO;
import com.miniso.ecomm.bootdemoapp.client.service.DemoDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class DemoDubboServiceImpl implements DemoDubboService {

    @DubboReference
    private DemoDubboService demoDubboService;

    @Override
    public Result<String> anyService(SomeDummyObjDTO someDummyObjDTO) {
        return Result.success("Your input:" + someDummyObjDTO);
    }
}
