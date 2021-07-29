package com.miniso.ecomm.bootdemoapp.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class SomeDummyObjDTO implements Serializable {

    private String someKey;

    private int anotherKey;
}
