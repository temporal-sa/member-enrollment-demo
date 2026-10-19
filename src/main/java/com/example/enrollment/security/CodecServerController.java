package com.example.enrollment.security;

import com.google.protobuf.util.JsonFormat;
import io.temporal.api.common.v1.Payload;
import io.temporal.api.common.v1.Payloads;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Codec server for the Temporal Web UI and CLI. The UI calls /decode from the browser so it can
 * show readable payloads without the Temporal service ever holding the key.
 *
 * Local dev server:  temporal server start-dev --ui-codec-endpoint http://localhost:8080/codec
 * Temporal Cloud:    namespace settings, Codec Server, enable "Pass the user access token".
 *
 * For a real deployment, validate the bearer token here (for example against Entra ID or the
 * Temporal Cloud JWKS) and check the caller's namespace and role before decoding.
 */
@RestController
@RequestMapping("/codec")
@CrossOrigin(
        origins = {"http://localhost:8233", "https://cloud.temporal.io"},
        allowedHeaders = {"authorization", "x-namespace", "content-type"},
        methods = {RequestMethod.POST, RequestMethod.OPTIONS})
public class CodecServerController {

    private final PhiCodec codec;
    private final boolean requireAuth;

    public CodecServerController(PhiCodec codec, @Value("${enrollment.codec.require-auth:false}") boolean requireAuth) {
        this.codec = codec;
        this.requireAuth = requireAuth;
    }

    @PostMapping(value = "/decode", produces = MediaType.APPLICATION_JSON_VALUE)
    public String decode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body)
            throws Exception {
        checkAuth(authorization);
        return convert(body, true);
    }

    @PostMapping(value = "/encode", produces = MediaType.APPLICATION_JSON_VALUE)
    public String encode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body)
            throws Exception {
        checkAuth(authorization);
        return convert(body, false);
    }

    private void checkAuth(String authorization) {
        if (requireAuth && (authorization == null || authorization.isBlank())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
    }

    private String convert(String json, boolean decode) throws Exception {
        Payloads.Builder in = Payloads.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, in);
        List<Payload> out = decode ? codec.decode(in.getPayloadsList()) : codec.encode(in.getPayloadsList());
        return JsonFormat.printer().print(Payloads.newBuilder().addAllPayloads(out).build());
    }
}
