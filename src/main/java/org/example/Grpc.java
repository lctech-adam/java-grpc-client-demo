package org.example;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import io.grpc.*;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class Grpc {
    public static class Options {
        public String endpoint;
        public Integer port;
        public String authority;
        public String audience;
        public String userAgent;
        public String serviceAccountFile;
        public Options(String endpoint, Integer port, String userAgent, String serviceAccountFile) {
            this.endpoint = endpoint;
            this.port = port;
            this.authority = endpoint;
            this.audience = endpoint;
            this.userAgent = userAgent;
            this.serviceAccountFile = serviceAccountFile;
        }
    }

    public static ManagedChannel newConn(Options options) throws IOException, IllegalArgumentException {
        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(options.endpoint, options.port);

        if (!options.authority.isEmpty()) {
            channelBuilder.overrideAuthority(options.authority);
        }
        channelBuilder.userAgent(options.userAgent);
        channelBuilder.sslContext(GrpcSslContexts.forClient().build());

        GoogleCredentials credentials;
        if (options.serviceAccountFile.isEmpty()) {
            credentials = GoogleCredentials.getApplicationDefault();
        } else {
            credentials = GoogleCredentials.fromStream(new FileInputStream(options.serviceAccountFile));
        }

        if (!(credentials instanceof IdTokenProvider idTokenProvider)) {
            throw new IllegalArgumentException("Credentials are not an instance of IdTokenProvider");
        }

        List<IdTokenProvider.Option> tokenOptions = new ArrayList<>();
        IdToken idToken = idTokenProvider.idTokenWithAudience(options.audience, tokenOptions);
        CallCredentials callCredentials = new CallCredentials() {
            @Override
            public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier metadataApplier) {
                Metadata metadata = new Metadata();
                metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + idToken.getTokenValue());
                metadataApplier.apply(metadata);
            }
        };
        return channelBuilder.intercept(new RpcInterceptor(callCredentials)).build();
    }

    public static class RpcInterceptor implements ClientInterceptor {
        private final CallCredentials callCredentials;

        public RpcInterceptor(CallCredentials callCredentials) {
            this.callCredentials = callCredentials;
        }

        @Override
        public <Req, Res> ClientCall<Req, Res> interceptCall(MethodDescriptor<Req, Res> methodDescriptor, CallOptions callOptions, Channel next) {
            return next.newCall(methodDescriptor, callOptions.withCallCredentials(this.callCredentials));
        }
    }
}
