package com.miniso.ecomm.bootdemoapp.contoller;

import com.miniso.ecomm.apigateway.client.services.lazada.LazadaOrderService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    private HttpServletRequest request;

    @DubboReference
    private LazadaOrderService lazadaOrderService;

    @GetMapping("/index")
    public ModelAndView index() {
        Map<String, Object> model = new HashMap<>();
        model.put("now", new Date());
        model.put("requestQuery", request.getQueryString());
        return new ModelAndView("demo", model);
    }

    @GetMapping("/lazada")
    public ResponseEntity<String> lazadaTest() {
        return ResponseEntity.ok(lazadaOrderService
                .getItemInfoOfOrder("", 123L).toString());
    }
}
