package com.miniso.ecomm.bootdemoapp.client.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SomeDummyObjDTO implements Serializable {

    private String someKey;

    private int anotherKey;
}
