package com.miniso.ecomm.bootdemoapp.schedule;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.apigateway.client.dto.ShopDTO;
import com.miniso.ecomm.apigateway.client.dto.amazon.report.AmazonReportDTO;
import com.miniso.ecomm.apigateway.client.dto.amazon.report.AmazonReportDocumentDTO;
import com.miniso.ecomm.apigateway.client.dto.lazada.order.OrderDTO;
import com.miniso.ecomm.apigateway.client.dto.lazada.order.OrderPageDTO;
import com.miniso.ecomm.apigateway.client.dto.shopee.payment.EscrowDetailDTO;
import com.miniso.ecomm.apigateway.client.enums.AmazonReportProcessingStatusEnum;
import com.miniso.ecomm.apigateway.client.enums.PlatformEnum;
import com.miniso.ecomm.apigateway.client.enums.SP_ResponseOptionalFiledEnum;
import com.miniso.ecomm.apigateway.client.request.amazon.AmazonOrderReportRequest;
import com.miniso.ecomm.apigateway.client.request.lazada.LazadaQueryOrdersRequest;
import com.miniso.ecomm.apigateway.client.request.shop.QueryShopPageRequest;
import com.miniso.ecomm.apigateway.client.request.shopee.ShopeeOrderDetailRequest;
import com.miniso.ecomm.apigateway.client.request.shopee.ShopeeOrderRequest;
import com.miniso.ecomm.apigateway.client.request.tokopedia.TokopediaOrderPageRequest;
import com.miniso.ecomm.apigateway.client.services.ShopService;
import com.miniso.ecomm.apigateway.client.services.amazon.AmazonOrderService;
import com.miniso.ecomm.apigateway.client.services.lazada.LazadaOrderService;
import com.miniso.ecomm.apigateway.client.services.shopee.ShopeeOrderService;
import com.miniso.ecomm.apigateway.client.services.shopee.ShopeePaymentService;
import com.miniso.ecomm.apigateway.client.services.tokopedia.TokopediaOrderService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.miniso.ecomm.bootdemoapp.schedule.ParameterUtils.*;

@Component
@Slf4j
public class FetchOrderItemsTask {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    @DubboReference
    private ShopService shopService;

    @DubboReference
    private LazadaOrderService lazadaOrderService;

    @DubboReference
    private ShopeeOrderService shopeeOrderService;

    @DubboReference(timeout = 60000)
    private TokopediaOrderService tokopediaOrderService;

    @DubboReference
    private AmazonOrderService amazonOrderService;

    @DubboReference
    private ShopeePaymentService shopeePaymentService;

    @Value("${lazada.order.batch.size:100}")
    private int lazadaOrderBatchSize;

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(5);

    private static final ExecutorService EXECUTOR_SERVICE_SHOPEE_FINANCE = new ThreadPoolExecutor(10, 10, 0
            , TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000), new ThreadPoolExecutor.CallerRunsPolicy());

    @XxlJob("fetchLazada")
    public ReturnT<String> fetchLazada(String dateRange) {
        return fetchLazadaBiz(dateRange, 8);
    }

    @XxlJob("fetchLazadaHourly")
    public ReturnT<String> fetchLazadaHourly(String dateRange) {
        return fetchLazadaBiz(dateRange, 1);
    }

    public ReturnT<String> fetchLazadaBiz(String dateRange, int hourInterval) {

        hourInterval = Math.max(Math.min(24, hourInterval), 1);
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];

        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);
        endDate = DateUtils.addMilliseconds(endDate, (int) (TimeUnit.DAYS.toMillis(1L) - 1));

        log.warn("Fetch lazada raw order-item data for:{} ~ {}, hourInterval:{}", startDay, endDate, hourInterval);

        List<ShopDTO> shopDTOList = getShops(PlatformEnum.LAZADA, dateRange);

        while (startDay.before(endDate)) {
            Date tempEndDate = DateUtil.addHours(startDay, hourInterval);
            final String createdAfter = ISO_DATE_FORMAT.format(startDay);
            final String createdBefore = ISO_DATE_FORMAT.format(tempEndDate);
            shopDTOList.forEach(shopDTO -> {
                EXECUTOR_SERVICE.execute(() -> {
                    AtomicInteger counter = new AtomicInteger(0);
                    LazadaQueryOrdersRequest ordersRequest = new LazadaQueryOrdersRequest();
                    ordersRequest.setCreatedAfter(createdAfter);
                    ordersRequest.setCreatedBefore(createdBefore);
                    ordersRequest.setOffset(0);
                    ordersRequest.setLimit(lazadaOrderBatchSize);
                    Thread.currentThread().setName(String.format("%s:%s~%s", shopDTO.getAccount(), createdAfter, createdBefore));

                    log.warn("Shop:{}, ordersRequest:{}", shopDTO.getAccount(), JSON.toJSONString(ordersRequest));

                    //先获取order；
                    Result<OrderPageDTO> orderPageDTO = lazadaOrderService.listItems4RangeOfOrders(shopDTO.getAccount(), ordersRequest);
                    final int totalOrders = orderPageDTO.getData().getCountTotal();
                    while (Result.isNonEmptyResult(orderPageDTO)) {
                        if (CollectionUtils.isNotEmpty(orderPageDTO.getData().getOrders())) {
                            log.warn("Shop:{}, total-orders:{}, running:{}",
                                    shopDTO.getAccount(), orderPageDTO.getData().getCountTotal(), ordersRequest.getOffset());
                            counter.addAndGet(orderPageDTO.getData().getOrders().size());
                            //再根据order ID获取order-item：
                            lazadaOrderService.getItemInfoOfOrders(shopDTO.getAccount(), orderPageDTO.getData().getOrders().stream()
                                    .map(OrderDTO::getOrderId).collect(Collectors.toList()));
                            ordersRequest.setOffset(counter.get());

                            if (ordersRequest.getOffset() >= totalOrders) {
                                log.warn("Reached end:{}", JSON.toJSONString(ordersRequest));
                                return;
                            }
                            orderPageDTO = lazadaOrderService.listItems4RangeOfOrders(shopDTO.getAccount(), ordersRequest);
                        } else {
                            log.warn("Reached end:{}", JSON.toJSONString(orderPageDTO));
                            return;
                        }
                    }
                    log.error("Shop:{}, ordersRequest:{}, return error:{}", shopDTO.getAccount(), JSON.toJSONString(ordersRequest), JSON.toJSONString(orderPageDTO));
                });
            });
            startDay = tempEndDate;
        }

        return new ReturnT("Schedule success");
    }

    @XxlJob("fetchShopee")
    public ReturnT<String> fetchShopee(String dateRange) {
        Date[] range = getDateRangeObj(dateRange);
        log.warn("Fetch shopee raw order-item data for:{} ~ {}", range[0], range[1]);

        getShops(PlatformEnum.SHOPEE, dateRange).forEach(shopDTO -> {
            final long shopId = Long.parseLong(shopDTO.getAccount());
            long startDay = range[0].getTime() / 1000L, endDate = range[1].getTime() / 1000L;

            final AtomicLong financeCounter = new AtomicLong(0L);

            while (startDay < endDate) {
                final long fromTime = startDay, toTime = startDay + TimeUnit.HOURS.toSeconds(12L);
                EXECUTOR_SERVICE.execute(() -> {
                    ShopeeOrderRequest request = new ShopeeOrderRequest();
                    request.setPageSize(50);
                    request.setTimeFrom(fromTime);
                    request.setTimeTo(toTime);
                    request.setTimeRangeField(ShopeeOrderRequest.TimeRangeFieldEnum.CREATE_TIME);
                    log.warn("Shopee request:{}", JSON.toJSONString(request));
                    //先获取order；
                    com.miniso.ecomm.apigateway.client.dto.shopee.order.OrderPageDTO orderPageDTO
                            = shopeeOrderService.queryOrderList(shopId, request).getData();


                    while (orderPageDTO != null) {
                        if (CollectionUtils.isNotEmpty(orderPageDTO.getOrderList())) {
                            log.warn("Shop:{}, has more:{}, nextUrl:{}",
                                    shopId, orderPageDTO.getMore(), orderPageDTO.getNextCursor());

                            //再根据order ID获取order-item：

                            ShopeeOrderDetailRequest detailRequest = new ShopeeOrderDetailRequest();
                            detailRequest.setOrderSNList(orderPageDTO.getOrderList().stream().map(orderInfo -> {
                                        // 根据order-sn获取财务数据；
                                        //异步
                                        EXECUTOR_SERVICE_SHOPEE_FINANCE.execute(() -> {
                                            Result<EscrowDetailDTO> escrowDetailDTOResult = shopeePaymentService.getEscrowDetail(shopId, orderInfo.getOrderSN());
                                            if (financeCounter.getAndIncrement() % 100 == 0) {
                                                log.warn("fetch shopee finance, shop:{}, result:{}", shopId, escrowDetailDTOResult.getData());
                                            }
                                        });
                                        return orderInfo.getOrderSN();
                                    })
                                    .collect(Collectors.joining(",")));
                            detailRequest.setResponseOptionalFieldEnums(Arrays.asList(SP_ResponseOptionalFiledEnum.values()));
                            log.warn("Shopee order-detail result:{}", shopeeOrderService.queryOrderDetail(shopId, detailRequest).isSuccess());

                            if (orderPageDTO.getMore()) {
                                request.setCursor(orderPageDTO.getNextCursor());
                                orderPageDTO = shopeeOrderService.queryOrderList(shopId, request).getData();
                            } else {
                                return;
                            }
                        } else {
                            return;
                        }
                    }
                });

                startDay = toTime;
            }
        });

        return new ReturnT("schedule success");
    }


    @XxlJob("fetchTokopedia")
    public ReturnT<String> fetchTokopedia(String dateRange) throws ParseException {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);

        for (ShopDTO shopDTO : getShops(PlatformEnum.TOKOPEDIA, dateRange)) {
            log.warn("Fetch tokopedia raw order-item data for:{} ~ {}", finalFromDay, finalToDay);
            final long shopId = Long.parseLong(shopDTO.getAccount());

            while (startDay.before(endDate)) {
                Date tempEndDate = DateUtil.addHours(startDay, 24);
                final long fromTime = startDay.getTime() / 1000L, toTime = tempEndDate.getTime() / 1000L;
                EXECUTOR_SERVICE.execute(() -> {
                    TokopediaOrderPageRequest pageRequest = new TokopediaOrderPageRequest();
                    pageRequest.setPerPage(50);
                    AtomicInteger pageCounter = new AtomicInteger(1);
                    pageRequest.setPage(pageCounter.getAndIncrement());
                    pageRequest.setFromDate(fromTime);
                    pageRequest.setToDate(toTime);
                    pageRequest.setShopId(shopId);
                    Result<List<com.miniso.ecomm.apigateway.client.dto.tokopedia.order.OrderDTO>> orderDTOs = getTokopediaAllOrders(shopId, pageRequest, 0);

                    if (Result.isFailed(orderDTOs)) {
                        throw new RuntimeException(JSON.toJSONString(orderDTOs, true));
                    }

                    //先获取order；
                    while (Result.isNonEmptyResult(orderDTOs)) {
                        log.warn("Shop:{}, order-items returned:{}",
                                shopDTO.getAccount(), orderDTOs.getData().size());
                        pageRequest.setPage(pageCounter.getAndIncrement());
                        orderDTOs = getTokopediaAllOrders(shopId, pageRequest, 0);
                    }
                });
                startDay = tempEndDate;
            }
        }
        return new ReturnT("schedule success");
    }

    private Result<List<com.miniso.ecomm.apigateway.client.dto.tokopedia.order.OrderDTO>> getTokopediaAllOrders(long shopId, TokopediaOrderPageRequest pageRequest, int retrying) {
        if (retrying++ > 3) {
            return Result.failed("too many retry:" + retrying);
        }
        log.warn(JSON.toJSONString(pageRequest));
        Result<List<com.miniso.ecomm.apigateway.client.dto.tokopedia.order.OrderDTO>> orderDTOs = tokopediaOrderService.getAllOrders(shopId, pageRequest);
        if (Result.isSuccess(orderDTOs)) {
            return orderDTOs;
        }
        log.warn(JSON.toJSONString(orderDTOs));
        if (retrying < 3) {
            try {
                TimeUnit.SECONDS.sleep(30);
                return getTokopediaAllOrders(shopId, pageRequest, retrying);
            } catch (InterruptedException e) {
                return Result.failed(e.getMessage());
            }
        }
        return orderDTOs;
    }

    @XxlJob("fetchAmazon")
    public ReturnT<String> fetchAmazon(String dateRange) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        final Date[] range = getDateRangeObj(dateRange);
        log.warn("Fetch amazon raw order-item data for:{} ~ {}", range[0], range[1]);

        final int secondsToWaitSeconds = 12 * 3600, queryReportIntervalSeconds = 20;

        List<ShopDTO> shopDTOList = getShops(PlatformEnum.AMAZON, dateRange);

        shopDTOList.forEach(shopDTO -> {
            Date fromDay = range[0], finalToDay = range[1];
            while (fromDay.before(finalToDay)) {
                Date tempEndDate = DateUtil.addDays(fromDay, 20);
                if (tempEndDate.after(finalToDay)) {
                    tempEndDate = finalToDay;
                }
                Date finalTempEndDate = tempEndDate;
                final Date finalFromDay = fromDay;
                EXECUTOR_SERVICE.execute(() -> {
                    final AtomicInteger counter = new AtomicInteger(0);
                    AmazonOrderReportRequest amazonOrderReportRequest = new AmazonOrderReportRequest();
                    amazonOrderReportRequest.setSellingPartner(shopDTO.getAccount());
                    amazonOrderReportRequest.setDataStartTime(simpleDateFormat.format(finalFromDay));
                    amazonOrderReportRequest.setDataEndTime(simpleDateFormat.format(finalTempEndDate));
                    Result<AmazonReportDTO> orderReportResult = amazonOrderService.createOrderReport(amazonOrderReportRequest);
                    log.warn("amazon shop:{}, request:{}", shopDTO.getAccount(), JSON.toJSONString(amazonOrderReportRequest));
                    if (Result.isNonEmptyResult(orderReportResult)) {
                        final String reportId = orderReportResult.getData().getReportId();
                        while (!AmazonReportDTO.isDone(orderReportResult.getData())) {
                            if (counter.getAndAdd(queryReportIntervalSeconds) >= secondsToWaitSeconds) {
                                log.error("", new Exception("wait too many seconds:" + counter.get()));
                                return;
                            }
                            if (AmazonReportProcessingStatusEnum.CANCELLED.equals(orderReportResult.getData().getProcessingStatus())) {
                                log.error("", new Exception("report been cancelled:" + orderReportResult.getData()));
                                return;
                            }
                            log.warn("shop:{}, report:{}", shopDTO.getAccount(), JSONObject.toJSONString(orderReportResult.getData()));
                            try {
                                TimeUnit.SECONDS.sleep(queryReportIntervalSeconds);
                            } catch (InterruptedException e) {
                                log.error("stopped unexpected, shop:{}, report:{}", shopDTO.getAccount(), JSONObject.toJSONString(orderReportResult.getData()));
                                log.error("", e);
                                return;
                            }
                            orderReportResult = amazonOrderService.getOrderReport(shopDTO.getAccount(), reportId);
                        }
                        Result<AmazonReportDocumentDTO> orderDocumentResult = amazonOrderService.getOrderDocumentUrl(shopDTO.getAccount(), orderReportResult.getData().getReportDocumentId());
                        log.warn("shop:{}, document:{}", shopDTO.getAccount(), JSON.toJSONString(orderDocumentResult));
                    } else {
                        log.warn("", new Exception("create amazon report failed:" + JSON.toJSONString(orderReportResult)));
                    }
                });
                fromDay = tempEndDate;
            }
        });

        return new ReturnT("Scheduled success");
    }

    private List<ShopDTO> getShopsByPlatform(PlatformEnum platformEnum) {
        QueryShopPageRequest shopRequest = new QueryShopPageRequest();
        shopRequest.setPlatform(platformEnum.getPlatformName());
        shopRequest.setPageSize(100);

        return shopService.getShopList(shopRequest).getData();
    }

    private List<ShopDTO> getShops(PlatformEnum platformEnum, String taskArgs) {
        List<ShopDTO> shopDTOS = getShopsByPlatform(platformEnum);
        String[] shopAccounts = getShopIds(taskArgs);
        if (null != shopAccounts && shopAccounts.length > 0) {
            shopDTOS = Arrays.stream(shopAccounts).map(account -> {
                ShopDTO shopDTO = new ShopDTO();
                shopDTO.setAccount(account);
                return shopDTO;
            }).collect(Collectors.toList());
        }
        return shopDTOS;
    }
}
