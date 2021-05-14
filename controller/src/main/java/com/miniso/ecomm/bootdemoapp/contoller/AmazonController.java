package com.miniso.ecomm.bootdemoapp.contoller;

import com.amazon.SellingPartnerAPIAA.AWSAuthenticationCredentials;
import com.amazon.SellingPartnerAPIAA.AWSAuthenticationCredentialsProvider;
import com.amazon.SellingPartnerAPIAA.LWAAuthorizationCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import io.swagger.client.ApiException;
import io.swagger.client.api.SellersApi;
import io.swagger.client.model.GetMarketplaceParticipationsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.amazon.SellingPartnerAPIAA.ScopeConstants.SCOPE_MIGRATION_API;
import static com.amazon.SellingPartnerAPIAA.ScopeConstants.SCOPE_NOTIFICATIONS_API;

@RestController
@RequestMapping("/platforms/amazon")
public class AmazonController {

    private static final String CLIENT_ID = "amzn1.application-oa2-client.57a6123f83d94bd88b15b0c8ab3ca601";

    private static final String CLIENT_SECRET = "5b8870857f4fab3391a269d8ecc4ef529d3dbb9adc5545f406f6b33b9ce731b7";

    private static final String IAM_USER_KEY = "AKIA5WHNKHQSQLNHLS24";

    private static final String IAM_USER_SECRET = "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";

    private static final String REFRESH_CODE = "";

    private static final String STATIC_QUERY_STRINGS =
            String.format("grant_type=authorization_code&code=%s&client_id=%s&client_secret=%s", REFRESH_CODE, null, null);

    @GetMapping("/sellers")
    public Object amazon() throws ApiException {
        String region = "us-east-1";

        BasicAWSCredentials
                awsCreds = new BasicAWSCredentials(IAM_USER_KEY, IAM_USER_SECRET);

        AWSCredentialsProvider
                credentialsProvider = new STSAssumeRoleSessionCredentialsProvider
                .Builder("myRoleArn", "uniqueNameForRoleSession")
                .withStsClient(AWSSecurityTokenServiceClientBuilder.standard()
                        .withRegion(region)
                        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                        .build())
                .build();

        LWAAuthorizationCredentials lwaAuthorizationCredentials =
                LWAAuthorizationCredentials.builder()
                        .clientId(CLIENT_ID)
                        .clientSecret(CLIENT_SECRET)
                        .withScopes(SCOPE_NOTIFICATIONS_API, SCOPE_MIGRATION_API)
                        .endpoint("https://api.amazon.com/auth/o2/token")
                        .build();


        SellersApi sellersApi = new SellersApi.Builder()
                .awsAuthenticationCredentialsProvider(AWSAuthenticationCredentialsProvider
                        .builder().roleArn("arn:aws:iam::941095533605:user/miniso-bos-iam").roleSessionName("sessionName").build())
                .lwaAuthorizationCredentials(lwaAuthorizationCredentials)
                .awsAuthenticationCredentials(AWSAuthenticationCredentials.builder()
                        .accessKeyId(IAM_USER_KEY).secretKey(IAM_USER_SECRET).region(region).build())
                .endpoint("https://sellingpartnerapi-na.amazon.com")
                .build();
        sellersApi.getMarketplaceParticipations();

        GetMarketplaceParticipationsResponse response = sellersApi.getMarketplaceParticipations();
        return response.getPayload();
    }
}
