package com.tonapps.wallet.api.cronet;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.chromium.net.CronetEngine;
import org.chromium.net.UrlRequest;

/** Converts OkHttp requests to Cronet requests. */
final class RequestResponseConverter {
    private static final String CONTENT_LENGTH_HEADER_NAME = "Content-Length";
    private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    private static final String CONTENT_TYPE_HEADER_DEFAULT_VALUE = "application/octet-stream";

    private final CronetEngine cronetEngine;
    private final Executor uploadDataProviderExecutor;
    private final ResponseConverter responseConverter;
    private final RequestBodyConverter requestBodyConverter;
    private final RedirectStrategy redirectStrategy;

    RequestResponseConverter(
            CronetEngine cronetEngine,
            Executor uploadDataProviderExecutor,
            RequestBodyConverter requestBodyConverter,
            ResponseConverter responseConverter,
            RedirectStrategy redirectStrategy) {
        this.cronetEngine = cronetEngine;
        this.uploadDataProviderExecutor = uploadDataProviderExecutor;
        this.requestBodyConverter = requestBodyConverter;
        this.responseConverter = responseConverter;
        this.redirectStrategy = redirectStrategy;
    }

    /**
     * Converts OkHttp's {@link Request} to a corresponding Cronet's {@link UrlRequest}.
     *
     * <p>Since Cronet doesn't have a notion of a Response, which is handled entirely from the
     * callbacks, this method also returns a {@link java.util.concurrent.Future} like object the
     * caller should use to obtain the matching {@link Response} for the given request. For example:
     *
     * <pre>
     *   RequestResponseConverter converter = ...
     *   CronetRequestAndOkHttpResponse reqResp = converter.convert(okHttpRequest);
     *   reqResp.getRequest.start();
     *
     *   // Will block until status code, headers... are available
     *   Response okHttpResponse = reqResp.getResponse();
     *
     *   // use OkHttp Response as usual
     * </pre>
     */
    CronetRequestAndOkHttpResponse convert(
            Request okHttpRequest, int readTimeoutMillis, int writeTimeoutMillis) throws IOException {

        OkHttpBridgeRequestCallback callback =
                new OkHttpBridgeRequestCallback(readTimeoutMillis, redirectStrategy);

        // The OkHttp request callback methods are lightweight, the heavy lifting is done by OkHttp /
        // app owned threads. Use a direct executor to avoid extra thread hops.
        UrlRequest.Builder builder =
                cronetEngine
                        .newUrlRequestBuilder(
                                okHttpRequest.url().toString(), callback, MoreExecutors.directExecutor())
                        .allowDirectExecutor();

        builder.setHttpMethod(okHttpRequest.method());

        for (int i = 0; i < okHttpRequest.headers().size(); i++) {
            builder.addHeader(okHttpRequest.headers().name(i), okHttpRequest.headers().value(i));
        }

        RequestBody body = okHttpRequest.body();

        if (body != null) {
            if (okHttpRequest.header(CONTENT_LENGTH_HEADER_NAME) == null && body.contentLength() != -1) {
                builder.addHeader(CONTENT_LENGTH_HEADER_NAME, String.valueOf(body.contentLength()));
            }

            if (body.contentLength() != 0) {
                if (body.contentType() != null) {
                    builder.addHeader(CONTENT_TYPE_HEADER_NAME, body.contentType().toString());
                } else if (okHttpRequest.header(CONTENT_TYPE_HEADER_NAME) == null) {
                    // Cronet always requires content-type to be present when a body is present. Use a generic
                    // value if one isn't provided.
                    builder.addHeader(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_HEADER_DEFAULT_VALUE);
                } // else use the header

                builder.setUploadDataProvider(
                        requestBodyConverter.convertRequestBody(body, writeTimeoutMillis),
                        uploadDataProviderExecutor);
            }
        }

        return new CronetRequestAndOkHttpResponse(
                builder.build(), createResponseSupplier(okHttpRequest, callback));
    }

    private ResponseSupplier createResponseSupplier(
            Request request, OkHttpBridgeRequestCallback callback) {
        return new ResponseSupplier() {
            @Override
            public Response getResponse() throws IOException {
                return responseConverter.toResponse(request, callback);
            }

            @Override
            public ListenableFuture<Response> getResponseFuture() {
                return responseConverter.toResponseAsync(request, callback);
            }
        };
    }

    /** A {@link Future} like holder for OkHttp's {@link Response}. */
    private interface ResponseSupplier {
        Response getResponse() throws IOException;

        ListenableFuture<Response> getResponseFuture();
    }

    /** A simple data class for bundling Cronet request and OkHttp response. */
    static final class CronetRequestAndOkHttpResponse {
        private final UrlRequest request;
        private final ResponseSupplier responseSupplier;

        CronetRequestAndOkHttpResponse(UrlRequest request, ResponseSupplier responseSupplier) {
            this.request = request;
            this.responseSupplier = responseSupplier;
        }

        public UrlRequest getRequest() {
            return request;
        }

        public Response getResponse() throws IOException {
            return responseSupplier.getResponse();
        }

        public ListenableFuture<Response> getResponseAsync() {
            return responseSupplier.getResponseFuture();
        }
    }
}
