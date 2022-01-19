package com.miniso.ecomm.bootdemoapp.contoller;

import com.miniso.ecomm.apigateway.client.services.lazada.LazadaOrderService;
import com.miniso.ecomm.apigateway.client.services.tokopedia.TokopediaOrderService;
import com.miniso.ecomm.bootdemoapp.schedule.FetchOrderItemsTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequestMapping("/demo")
@Slf4j
public class DemoController {

    @Value("${order.level.data.root:/data/orders}")
    private String tempRootPath;

    @Autowired
    private HttpServletRequest request;

    @DubboReference
    private LazadaOrderService lazadaOrderService;

    @Autowired
    private FetchOrderItemsTask fetchOrderItemsTask;

    @Autowired
    private TokopediaOrderService tokopediaOrderService;

    @GetMapping("/index")
    public ModelAndView index() {
        Map<String, Object> model = new HashMap<>();
        model.put("now", new Date());
        model.put("fetch", fetchOrderItemsTask.fetchLazada(null));
        model.put("requestQuery", request.getQueryString());
        return new ModelAndView("demo", model);
    }

    @GetMapping("/lazada")
    public ResponseEntity<String> lazadaTest() {
        return ResponseEntity.ok(lazadaOrderService
                .getItemInfoOfOrder("", 123L).toString());
    }

    private static final ExecutorService LAZADA_EXECUTOR = new ThreadPoolExecutor(5, 5, 0,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000), new ThreadPoolExecutor.CallerRunsPolicy());

    @PostMapping("/order/lazada")
    public ResponseEntity<String> lazadaFetchOrderLevel(
            @RequestParam(required = false, defaultValue = "lazada-no-payment-info-orders.csv") String file)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(tempRootPath + "/" + file))) {
            String line = reader.readLine();
            AtomicInteger counter = new AtomicInteger(0);
            while (null != line) {
                counter.incrementAndGet();
                if (line.equalsIgnoreCase("shop_id,order_id,cnt")) {
                    log.warn("this is the header:" + line);
                    continue;
                }
                final String[] params = line.split(",");
                if (params.length < 2) {
                    log.error("Invalid line:" + line);
                    continue;
                }
                LAZADA_EXECUTOR.execute(() -> {
                    lazadaOrderService.listItemsOfSingleOrder(params[0], Long.parseLong(params[1]));
                });
            }
            return ResponseEntity.ok("OK, lines:" + counter.get());
        }
    }


    private static final ExecutorService TOKOPEDIA_EXECUTOR = new ThreadPoolExecutor(5, 5, 0,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000), new ThreadPoolExecutor.CallerRunsPolicy());


    @PostMapping("/order/tokopedia")
    public ResponseEntity<String> tokopediaFetchOrderLevel(
            @RequestParam(required = false, defaultValue = "tokopedia-no-payment-info-orders.csv") String file)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(tempRootPath + "/" + file))) {
            String line = reader.readLine();
            AtomicInteger counter = new AtomicInteger(0);
            while (null != line) {
                counter.incrementAndGet();
                if (line.equalsIgnoreCase("shop_id,order_id,cnt")) {
                    log.warn("this is the header:" + line);
                    continue;
                }
                final String[] params = line.split(",");
                if (params.length < 2) {
                    log.error("Invalid line:" + line);
                    continue;
                }
                TOKOPEDIA_EXECUTOR.execute(() -> {
                    tokopediaOrderService.getSingleOrder(Long.parseLong(params[0]), Long.parseLong(params[1]));
                });
            }
            return ResponseEntity.ok("OK, lines:" + counter.get());
        }
    }
}
