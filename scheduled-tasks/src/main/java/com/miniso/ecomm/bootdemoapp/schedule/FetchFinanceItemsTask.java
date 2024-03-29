package com.miniso.ecomm.bootdemoapp.schedule;

import com.alibaba.fastjson.JSON;
import com.miniso.boot.client.result.Result;
import com.miniso.ecomm.apigateway.client.dto.ShopDTO;
import com.miniso.ecomm.apigateway.client.dto.lazada.finance.TransactionDTO;
import com.miniso.ecomm.apigateway.client.dto.shopee.payment.EscrowDTO;
import com.miniso.ecomm.apigateway.client.dto.tokopedia.payment.PaymentDTO;
import com.miniso.ecomm.apigateway.client.enums.PlatformEnum;
import com.miniso.ecomm.apigateway.client.request.lazada.LazadaQueryTransactionDetailRequest;
import com.miniso.ecomm.apigateway.client.request.shop.QueryShopPageRequest;
import com.miniso.ecomm.apigateway.client.request.shopee.ShopeeEscrowListRequest;
import com.miniso.ecomm.apigateway.client.request.tokopedia.TokopediaPaymentPageRequest;
import com.miniso.ecomm.apigateway.client.services.ShopService;
import com.miniso.ecomm.apigateway.client.services.lazada.LazadaPaymentService;
import com.miniso.ecomm.apigateway.client.services.shopee.ShopeePaymentService;
import com.miniso.ecomm.apigateway.client.services.tokopedia.TokopediaPaymentService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.miniso.ecomm.bootdemoapp.schedule.ParameterUtils.getDateRange;
import static com.miniso.ecomm.bootdemoapp.schedule.ParameterUtils.getShopIds;

@Component
@Slf4j
public class FetchFinanceItemsTask {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @DubboReference
    private ShopService shopService;

    @DubboReference(timeout = 60000)
    private LazadaPaymentService lazadaPaymentService;

    @DubboReference(timeout = 60000)
    private TokopediaPaymentService tokopediaPaymentService;

    @DubboReference(timeout = 60000)
    private ShopeePaymentService shopeePaymentService;

    @Value("${tokopedia.payment.service.retry.interval:90}")
    private int retryInterval;

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(5);

    @XxlJob("fetchFinanceLazada")
    public ReturnT<String> fetchLazada(String dateRange) {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        log.warn("Fetch lazada raw order-item data for:{} ~ {}", finalFromDay, finalToDay);


        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);

        final int limit = 500;

        List<ShopDTO> shopDTOList = getShops(PlatformEnum.LAZADA, dateRange);

        while (startDay.before(endDate)) {
            Date tempEndDate = DateUtil.addHours(startDay, 24);

            Date finalStartDay = startDay;
            shopDTOList.forEach(shopDTO -> {
                EXECUTOR_SERVICE.execute(() -> {

                    AtomicInteger counter = new AtomicInteger(0);
                    LazadaQueryTransactionDetailRequest paymentRequest = new LazadaQueryTransactionDetailRequest();
                    paymentRequest.setStartTime(finalStartDay);
                    paymentRequest.setEndTime(finalStartDay);
                    paymentRequest.setLimit(limit + "");
                    paymentRequest.setOffset("0");

                    log.warn("Shop:{}, ordersRequest:{}", shopDTO.getAccount(), JSON.toJSONString(paymentRequest));

                    Result<List<TransactionDTO>> listResult = lazadaPaymentService.getTransactionDetail(shopDTO.getAccount(), paymentRequest);
                    while (Result.isNonEmptyResult(listResult)) {
                        paymentRequest.setOffset(counter.addAndGet(listResult.getData().size()) + "");
                        log.warn("Shop:{}, ordersRequest:{}", shopDTO.getAccount(), JSON.toJSONString(paymentRequest));

                        if (listResult.getData().size() < limit) {
                            break;
                        }

                        listResult = lazadaPaymentService.getTransactionDetail(shopDTO.getAccount(), paymentRequest);
                    }
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
        log.warn("Fetch tokopedia raw finance-item data for:{} ~ {}", finalFromDay, finalToDay);

        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);

        List<ShopDTO> shopDTOList = getShops(PlatformEnum.TOKOPEDIA, dateRange);

        while (startDay.before(endDate)) {
            Date tempEndDate = DateUtil.addDays(startDay, 1);
            final Date finalStartDay = startDay;
            shopDTOList.forEach(shopDTO -> {
                final long shopId = Long.parseLong(shopDTO.getAccount());
                AtomicInteger pageCounter = new AtomicInteger(1);

                TokopediaPaymentPageRequest paymentRequest = new TokopediaPaymentPageRequest();
                paymentRequest.setFromDate(SIMPLE_DATE_FORMAT.format(finalStartDay));
                paymentRequest.setToDate(SIMPLE_DATE_FORMAT.format(tempEndDate));
                paymentRequest.setPerPage(500);
                paymentRequest.setPage(pageCounter.getAndIncrement());
                Result<PaymentDTO> paymentDTOResult = getSaldoHistoryWithRetry(shopId, paymentRequest, 2);
                while (Result.isNonEmptyResult(paymentDTOResult)) {
                    log.warn("Shop:{}, finance-items-returned:{}, request:{}",
                            shopDTO.getAccount(), paymentDTOResult.getData().getSaldoHistory().size(), JSON.toJSONString(paymentRequest));
                    if (paymentDTOResult.getData().getSaldoHistory().size() <= 0) {
                        log.warn("tokopedia-finance-item finished:{}", JSON.toJSONString(paymentRequest));
                        return;
                    }
                    paymentRequest.setPage(pageCounter.getAndIncrement());
                    paymentDTOResult = getSaldoHistoryWithRetry(shopId, paymentRequest, 2);
                }
            });
            startDay = tempEndDate;
        }

        return new ReturnT("scheduled success");
    }

    @XxlJob("fetchFinanceShopee")
    public ReturnT<String> fetchShopee(String dateRange) {
        String[] range = getDateRange(dateRange);
        final String finalFromDay = range[0];
        final String finalToDay = range[1];
        final int pageSize = 100;
        log.warn("Fetch shopee raw Escrow-List data for:{} ~ {}", finalFromDay, finalToDay);

        Date startDay = DateUtil.parseDate(finalFromDay);
        Date endDate = DateUtil.parseDate(finalToDay);

        List<ShopDTO> shopDTOList = getShops(PlatformEnum.SHOPEE, dateRange);

        while (startDay.before(endDate)) {
            Date tempEndDate = DateUtil.addDays(startDay, 1);
            final Date finalStartDay = startDay;
            shopDTOList.forEach(shopDTO -> {
                EXECUTOR_SERVICE.execute(() -> {
                    final long shopId = Long.parseLong(shopDTO.getAccount());
                    AtomicInteger pageCounter = new AtomicInteger(1);

                    ShopeeEscrowListRequest escrowListRequest = new ShopeeEscrowListRequest();
                    escrowListRequest.setPageSize(pageSize);
                    escrowListRequest.setPageNo(pageCounter.getAndIncrement());
                    escrowListRequest.setReleaseTimeFrom(finalStartDay.getTime() / 1000L);
                    escrowListRequest.setReleaseTimeTo(tempEndDate.getTime() / 1000L);

                    Result<EscrowDTO> escrowDTOResult = shopeePaymentService.getEscrowList(shopId, escrowListRequest);
                    while (Result.isSuccess(escrowDTOResult)) {
                        if (!Result.isNonEmptyResult(escrowDTOResult)) {
                            log.warn("Fetch shopee raw Escrow-List task ended:{} ~ {}", escrowListRequest, escrowDTOResult);
                            return;
                        }
                        log.warn("Fetch shopee raw Escrow-List task returned:{} : {}", escrowListRequest, escrowDTOResult.getData().getEscrowList().size());
                        if (escrowDTOResult.getData().getMore() || escrowDTOResult.getData().getEscrowList().size() >= pageSize) {
                            escrowListRequest.setPageNo(pageCounter.getAndIncrement());
                            escrowDTOResult = shopeePaymentService.getEscrowList(shopId, escrowListRequest);
                        } else {
                            log.warn("Fetch shopee raw Escrow-List task ended:{}", escrowListRequest);
                            return;
                        }
                    }
                    log.warn("Fetch shopee raw Escrow-List task ended up with failed result:{}", escrowDTOResult);
                });
            });
            startDay = tempEndDate;
        }

        return new ReturnT("scheduled success");
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

    private Result<PaymentDTO> getSaldoHistoryWithRetry(long shopId, TokopediaPaymentPageRequest paymentRequest, int retrying) {
        if (retrying <= 0) {
            return Result.failed("retry timeout");
        }
        while (retrying-- > 0) {
            Result<PaymentDTO> paymentDTOResult = tokopediaPaymentService.getSaldoHistory(shopId, paymentRequest);
            if (Result.isSuccess(paymentDTOResult)) {
                return paymentDTOResult;
            } else {
                log.warn(paymentDTOResult.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(retryInterval);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return Result.failed("retry timeout");
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
}
