package com.miniso.ecomm.bootdemoapp.schedule;

import com.google.common.collect.Lists;
import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.apigateway.client.dto.ShopDTO;
import com.miniso.ecomm.apigateway.client.dto.lazada.order.OrderDTO;
import com.miniso.ecomm.apigateway.client.dto.lazada.order.OrderPageDTO;
import com.miniso.ecomm.apigateway.client.enums.PlatformEnum;
import com.miniso.ecomm.apigateway.client.enums.SP_ResponseOptionalFiledEnum;
import com.miniso.ecomm.apigateway.client.request.lazada.LazadaQueryOrdersRequest;
import com.miniso.ecomm.apigateway.client.request.shop.QueryShopPageRequest;
import com.miniso.ecomm.apigateway.client.request.shopee.ShopeeOrderDetailRequest;
import com.miniso.ecomm.apigateway.client.request.shopee.ShopeeOrderRequest;
import com.miniso.ecomm.apigateway.client.request.tokopedia.TokopediaOrderPageRequest;
import com.miniso.ecomm.apigateway.client.services.ShopService;
import com.miniso.ecomm.apigateway.client.services.lazada.LazadaOrderService;
import com.miniso.ecomm.apigateway.client.services.shopee.ShopeeOrderService;
import com.miniso.ecomm.apigateway.client.services.tokopedia.TokopediaOrderService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import com.xxl.job.core.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FetchOrderItemsTask {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final long ONE_DAY_IN_MILLISECONDS = 24 * 3600 * 1000L;

    private static final String START_TIME_SUFFIX = "T00:00:00.000+08:00";

    private static final String END_TIME_SUFFIX = "T23:59:59.999+08:00";

    @DubboReference
    private ShopService shopService;

    @DubboReference
    private LazadaOrderService lazadaOrderService;

    @DubboReference
    private ShopeeOrderService shopeeOrderService;

    @DubboReference
    private TokopediaOrderService tokopediaOrderService;

    @XxlJob("fetchLazada")
    public ReturnT<String> fetchLazada(String dateRange) {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        XxlJobLogger.log("Fetch lazada raw order-item data for:{} ~ {}", finalFromDay, finalToDay);


        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);

        List<String> resultString = Lists.newLinkedList();

        while (startDay.before(endDate)) {
            Date tempEndDate = DateUtil.addHours(startDay, 12);
            final String createdAfter = DateUtil.formatDate(startDay) + START_TIME_SUFFIX;
            final String createdBefore = DateUtil.formatDate(tempEndDate) + START_TIME_SUFFIX;
            getShopsByPlatform(PlatformEnum.LAZADA).forEach(shopDTO -> {
                AtomicInteger counter = new AtomicInteger(0);
                LazadaQueryOrdersRequest ordersRequest = new LazadaQueryOrdersRequest();
                ordersRequest.setCreatedAfter(createdAfter);
                ordersRequest.setCreatedBefore(createdBefore);
                ordersRequest.setOffset(0);

                //先获取order；
                OrderPageDTO orderPageDTO = lazadaOrderService.listItems4RangeOfOrders(shopDTO.getAccount(), ordersRequest).getData();
                while (orderPageDTO != null) {
                    if (CollectionUtils.isNotEmpty(orderPageDTO.getOrders())) {
                        XxlJobLogger.log("Shop:{}, total-orders:{}, running:{}",
                                shopDTO.getAccount(), orderPageDTO.getCountTotal(), ordersRequest.getOffset());
                        counter.addAndGet(orderPageDTO.getOrders().size());
                        //再根据order ID获取order-item：
                        lazadaOrderService.getItemInfoOfOrders(shopDTO.getAccount(), orderPageDTO.getOrders().stream()
                                .map(OrderDTO::getOrderId).collect(Collectors.toList()));
                        ordersRequest.setOffset(counter.get());

                        orderPageDTO = lazadaOrderService.listItems4RangeOfOrders(shopDTO.getAccount(), ordersRequest).getData();
                    } else {
                        break;
                    }
                }
                resultString.add(String.format("Shop:%s, total-items:%d", shopDTO.getAccount(), counter.get()));
            });
            startDay = tempEndDate;
        }

        return new ReturnT(resultString);
    }

    @XxlJob("fetchShopee")
    public ReturnT<String> fetchShopee(String dateRange) {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        XxlJobLogger.log("Fetch shopee raw order-item data for:{} ~ {}", finalFromDay, finalToDay);

        List<String> resultString = Lists.newLinkedList();
        getShopsByPlatform(PlatformEnum.SHOPEE).forEach(shopDTO -> {
            final long shopId = Long.parseLong(shopDTO.getAccount());
            AtomicInteger counter = new AtomicInteger(0);


            ShopeeOrderRequest request = new ShopeeOrderRequest();
            request.setPageSize(10);
            request.setTimeFrom(ISODateTimeFormat.dateTimeParser().parseDateTime(finalFromDay + START_TIME_SUFFIX).getMillis());
            request.setTimeTo(ISODateTimeFormat.dateTimeParser().parseDateTime(finalToDay + START_TIME_SUFFIX).getMillis());
            com.miniso.ecomm.apigateway.client.dto.shopee.order.OrderPageDTO orderPageDTO
                    = shopeeOrderService.queryOrderList(shopId, request).getData();

            //先获取order；
            while (orderPageDTO != null) {
                if (CollectionUtils.isNotEmpty(orderPageDTO.getOrderList())) {
                    XxlJobLogger.log("Shop:{}, has more:{}, nextUrl:{}",
                            shopDTO.getAccount(), orderPageDTO.getMore(), orderPageDTO.getNextCursor());
                    counter.addAndGet(orderPageDTO.getOrderList().size());
                    //再根据order ID获取order-item：

                    ShopeeOrderDetailRequest detailRequest = new ShopeeOrderDetailRequest();
                    detailRequest.setOrderSNList(orderPageDTO.getOrderList().stream().map(
                                    com.miniso.ecomm.apigateway.client.dto.shopee.order.OrderPageDTO.OrderInfo::getOrderSN)
                            .collect(Collectors.joining(",")));
                    detailRequest.setResponseOptionalFieldEnums(Arrays.asList(SP_ResponseOptionalFiledEnum.values()));
                    XxlJobLogger.log("Shopee order-detail result:{}", shopeeOrderService.queryOrderDetail(shopId, detailRequest).isSuccess());

                    if (orderPageDTO.getMore()) {
                        request.setCursor(orderPageDTO.getNextCursor());
                        orderPageDTO = shopeeOrderService.queryOrderList(shopId, request).getData();
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            resultString.add(String.format("Shop:%s, total-items:%d", shopDTO.getAccount(), counter.get()));
        });

        return new ReturnT(resultString);
    }


    @XxlJob("fetchTokopedia")
    public ReturnT<String> fetchTokopedia(String dateRange) {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        XxlJobLogger.log("Fetch tokopedia raw order-item data for:{} ~ {}", finalFromDay, finalToDay);


        List<String> resultString = Lists.newLinkedList();
        getShopsByPlatform(PlatformEnum.TOKOPEDIA).forEach(shopDTO -> {
            final long shopId = Long.parseLong(shopDTO.getAccount());
            AtomicInteger counter = new AtomicInteger(0);

            TokopediaOrderPageRequest pageRequest = new TokopediaOrderPageRequest();
            pageRequest.setPerPage(50);
            AtomicInteger pageCounter = new AtomicInteger(1);
            pageRequest.setPage(pageCounter.getAndIncrement());
            pageRequest.setFromDate(ISODateTimeFormat.dateTimeParser().parseDateTime(finalFromDay + START_TIME_SUFFIX).getMillis());
            pageRequest.setToDate(ISODateTimeFormat.dateTimeParser().parseDateTime(finalToDay + START_TIME_SUFFIX).getMillis());
            pageRequest.setShopId(shopId);
            Result<List<com.miniso.ecomm.apigateway.client.dto.tokopedia.order.OrderDTO>> orderDTOs = tokopediaOrderService.getAllOrders(shopId, pageRequest);

            //先获取order；
            while (Result.isNonEmptyResult(orderDTOs)) {
                XxlJobLogger.log("Shop:{}, items-returned:{}",
                        shopDTO.getAccount(), orderDTOs.getData().size());
                pageRequest.setPage(pageCounter.getAndIncrement());
                orderDTOs = tokopediaOrderService.getAllOrders(shopId, pageRequest);
            }
            resultString.add(String.format("Shop:%s, total-items:%d", shopDTO.getAccount(), counter.get()));
        });

        return new ReturnT(resultString);
    }


    private List<ShopDTO> getShopsByPlatform(PlatformEnum platformEnum) {
        QueryShopPageRequest shopRequest = new QueryShopPageRequest();
        shopRequest.setPlatform(platformEnum.getPlatformName());
        shopRequest.setPageSize(10);

        return shopService.getShopList(shopRequest).getData().stream().filter(shopDTO -> {
            return platformEnum.getPlatformName().equals(shopDTO.getPlatform());
        }).collect(Collectors.toList());
    }

    private static String[] getDateRange(String dateRange) {
        String toDay = SIMPLE_DATE_FORMAT.format(new Date(System.currentTimeMillis() - ONE_DAY_IN_MILLISECONDS));
        String fromDay = SIMPLE_DATE_FORMAT.format(new Date(System.currentTimeMillis() - 30 * ONE_DAY_IN_MILLISECONDS));
        if (!StringUtils.isEmpty(dateRange)) {
            String[] range = dateRange.split(":");
            if (range.length > 1) {
                return range;
            }
        }

        return new String[]{fromDay, toDay};
    }
}
