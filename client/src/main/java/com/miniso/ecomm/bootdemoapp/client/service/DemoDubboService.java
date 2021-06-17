package com.miniso.ecomm.bootdemoapp.client.service;

import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.bootdemoapp.client.dto.SomeDummyObjDTO;

public interface DemoDubboService {
    Result<String> anyService(SomeDummyObjDTO someDummyObjDTO);
}
