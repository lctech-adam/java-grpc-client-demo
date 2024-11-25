package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pan.direct.account.JpassUpgraderCommandGrpc;
import pan.direct.account.JpassUpgraderCommandPb;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        String appID = "uuid-app-id";
        String privateKey = "hexadecimal-private-key";
        String kid = "uuid-key-id";

        String appCode;
        try {
            appCode = new AppCode(appID, kid, privateKey).generate();
        } catch (JsonProcessingException e) {
            logger.error("Got error when creating app code:", e);
            return;
        }

        String endpoint = "gapi.pan-direct.jkf.io";
        int port = 443;
        String userAgent = "local-test";
        String saPath = "/Users/adamchiu/lctech/credential/service-account/pan-direct-service-account.json";

        Grpc.Options connOption = new Grpc.Options(endpoint, port, userAgent, saPath);
        ManagedChannel channel;
        try {
            channel = Grpc.newConn(connOption);
        } catch (IOException e) {
            logger.error("Failed to create connection: {}", e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            logger.error("No valid id token: {}", e.getMessage());
            return;
        }

        JpassUpgraderCommandPb.CreateJPassUpgradeTokenPayload in = JpassUpgraderCommandPb.CreateJPassUpgradeTokenPayload.newBuilder()
                .setPanAppCode(appCode)
                .setAppMemberId("jvid_uid")
                .setAppMemberPhone("886900100100")
                .setAppMemberEmail("tester@lctech.com.tw")
                .setAppMemberName("jvid_channel_name")
                .build();

        List<Status.Code> possibleErrors = List.of(
                Status.Code.INVALID_ARGUMENT,
                Status.Code.UNAUTHENTICATED,
                Status.Code.PERMISSION_DENIED,
                Status.Code.INTERNAL);

        try {
            JpassUpgraderCommandGrpc.JpassUpgraderCommandBlockingStub blockingStub = JpassUpgraderCommandGrpc.newBlockingStub(channel);
            JpassUpgraderCommandPb.CreateJPassUpgradeTokenAnswer out = blockingStub.createJPassUpgradeToken(in);
            System.out.println("Received jpass upgrade token: " + out.getJpassUpgradeToken());
        } catch (StatusRuntimeException e) {
            Status status = e.getStatus();
            if (possibleErrors.contains(status.getCode())) {
                logger.error(status.getDescription());
            }
        }

        channel.shutdown();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            logger.error("Failed to terminate channel. Forcing shutdown.");
            channel.shutdownNow();
        }
        System.out.println("Channel shutdown");
    }
}