package com.miniso.ecomm.bootdemoapp.schedule;

import com.alibaba.fastjson.JSON;
import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.apigateway.client.dto.ShopDTO;
import com.miniso.ecomm.apigateway.client.dto.tokopedia.payment.PaymentDTO;
import com.miniso.ecomm.apigateway.client.enums.PlatformEnum;
import com.miniso.ecomm.apigateway.client.request.lazada.LazadaQueryOrdersRequest;
import com.miniso.ecomm.apigateway.client.request.shop.QueryShopPageRequest;
import com.miniso.ecomm.apigateway.client.request.tokopedia.TokopediaPaymentPageRequest;
import com.miniso.ecomm.apigateway.client.services.ShopService;
import com.miniso.ecomm.apigateway.client.services.amazon.AmazonOrderService;
import com.miniso.ecomm.apigateway.client.services.lazada.LazadaPaymentService;
import com.miniso.ecomm.apigateway.client.services.tokopedia.TokopediaOrderService;
import com.miniso.ecomm.apigateway.client.services.tokopedia.TokopediaPaymentService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import com.xxl.job.core.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class FetchFinanceItemsTask {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final long ONE_DAY_IN_MILLISECONDS = 24 * 3600 * 1000L;

    private static final String START_TIME_SUFFIX = "T00:00:00.000+08:00";

    private static final String END_TIME_SUFFIX = "T23:59:59.999+08:00";

    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    @DubboReference
    private ShopService shopService;

    @DubboReference
    private LazadaPaymentService lazadaPaymentService;

    @DubboReference
    private TokopediaOrderService tokopediaOrderService;

    @DubboReference
    private TokopediaPaymentService tokopediaPaymentService;

    @DubboReference
    private AmazonOrderService amazonOrderService;

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(5);

    @XxlJob("fetchFinanceLazada")
    public ReturnT<String> fetchLazada(String dateRange) {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        XxlJobLogger.log("Fetch lazada raw order-item data for:{} ~ {}", finalFromDay, finalToDay);


        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);

        while (startDay.before(endDate)) {
            Date tempEndDate = DateUtil.addHours(startDay, 8);
            final String createdAfter = ISO_DATE_FORMAT.format(startDay);
            final String createdBefore = ISO_DATE_FORMAT.format(tempEndDate);
            getShopsByPlatform(PlatformEnum.LAZADA).forEach(shopDTO -> {
                EXECUTOR_SERVICE.execute(() -> {
                    AtomicInteger counter = new AtomicInteger(0);
                    LazadaQueryOrdersRequest ordersRequest = new LazadaQueryOrdersRequest();
                    ordersRequest.setCreatedAfter(createdAfter);
                    ordersRequest.setCreatedBefore(createdBefore);
                    ordersRequest.setOffset(0);


                    XxlJobLogger.log("Shop:{}, ordersRequest:{}", shopDTO.getAccount(), JSON.toJSONString(ordersRequest));
                });
            });
            startDay = tempEndDate;
        }

        return new ReturnT("Schedule success");
    }

    @XxlJob("fetchFinanceTokopedia")
    public ReturnT<String> fetchTokopedia(String dateRange) {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        XxlJobLogger.log("Fetch tokopedia raw finance-item data for:{} ~ {}", finalFromDay, finalToDay);

        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);

        while (startDay.before(endDate)) {
            Date tempEndDate = DateUtil.addDays(startDay, 1);
            final Date finalStartDay = startDay;
            getShopsByPlatform(PlatformEnum.TOKOPEDIA).forEach(shopDTO -> {
                final long shopId = Long.parseLong(shopDTO.getAccount());
                AtomicInteger pageCounter = new AtomicInteger(1);

                TokopediaPaymentPageRequest paymentRequest = new TokopediaPaymentPageRequest();
                paymentRequest.setFromDate(SIMPLE_DATE_FORMAT.format(finalStartDay));
                paymentRequest.setToDate(SIMPLE_DATE_FORMAT.format(tempEndDate));
                paymentRequest.setPerPage(500);
                Result<PaymentDTO> paymentDTOResult = getSaldoHistoryWithRetry(shopId, paymentRequest, 2);
                if (pageCounter.get() % 20 == 0) {
                    XxlJobLogger.log("shop:{}, finance result:{}", shopId, paymentDTOResult.getData());
                }
                while (Result.isNonEmptyResult(paymentDTOResult)) {
                    XxlJobLogger.log("Shop:{}, items-returned:{}",
                            shopDTO.getAccount(), paymentDTOResult.getData().getSaldoHistory().size());
                    paymentRequest.setPage(pageCounter.getAndIncrement());
                    paymentDTOResult = getSaldoHistoryWithRetry(shopId, paymentRequest, 2);
                }
            });
            startDay = tempEndDate;
        }

        return new ReturnT("scheduled success");
    }

    private Result<PaymentDTO> getSaldoHistoryWithRetry(long shopId, TokopediaPaymentPageRequest paymentRequest, int retrying) {
        if (retrying <= 0) {
            return Result.failed("retry timeouted");
        }
        while (retrying-- > 0) {
            Result<PaymentDTO> paymentDTOResult = tokopediaPaymentService.getSaldoHistory(shopId, paymentRequest);
            if (Result.isSuccess(paymentDTOResult)) {
                return paymentDTOResult;
            } else {
                try {
                    TimeUnit.SECONDS.sleep(90);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @XxlJob("fetchFinanceAmazon")
    public ReturnT<String> fetchAmazon(String dateRange) {

        return new ReturnT("Scheduled success");
    }


    private List<ShopDTO> getShopsByPlatform(PlatformEnum platformEnum) {
        QueryShopPageRequest shopRequest = new QueryShopPageRequest();
        shopRequest.setPlatform(platformEnum.getPlatformName());
        shopRequest.setPageSize(100);

        return shopService.getShopList(shopRequest).getData();
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
