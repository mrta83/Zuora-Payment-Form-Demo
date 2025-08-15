/**
 * MIT License
 *
 * Copyright (c) [2024] Zuora, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.zuora.sample;

import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.get;
import static spark.Spark.staticFiles;

import com.zuora.ZuoraClient;
import com.zuora.model.*;

import com.google.gson.Gson;
import spark.utils.StringUtils;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.Map;
import com.google.gson.reflect.TypeToken;


public class Server {

    private static Gson gson = new Gson();

    private static String CLIENT_ID;
    private static String CLIENT_SECRET;
    private static String ZUORA_ENV_STR;
    private static final ZuoraClient zuoraClient;

    static {
        try {
            java.nio.file.Path envPath = Paths.get(".env");
            if (Files.exists(envPath)) {
                try (Stream<String> lines = Files.lines(envPath, StandardCharsets.UTF_8)) {
                    lines.map(String::trim)
                         .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                         .forEach(l -> {
                             int eq = l.indexOf('=');
                             String k = l.substring(0, eq).trim();
                             String v = l.substring(eq + 1).trim();
                             // Strip surrounding quotes if present
                             if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                                 v = v.substring(1, v.length() - 1);
                             }
                             // If env var is not already set in the system environment, set as system property
                             if (System.getenv(k) == null) {
                                 System.setProperty(k, v);
                             }
                         });
                }
            }
        } catch (Exception e) {
            System.out.println("Could not load .env file: " + e.getMessage());
        }

            CLIENT_ID = System.getenv("CLIENT_ID");
            if (CLIENT_ID == null) CLIENT_ID = System.getProperty("CLIENT_ID");
            CLIENT_SECRET = System.getenv("CLIENT_SECRET");
            if (CLIENT_SECRET == null) CLIENT_SECRET = System.getProperty("CLIENT_SECRET");
            ZUORA_ENV_STR = System.getenv("ZUORA_ENV");
            if (ZUORA_ENV_STR == null) ZUORA_ENV_STR = System.getProperty("ZUORA_ENV");

            // Validate required configuration - no fallbacks allowed
            String[] requiredKeys = new String[]{"CLIENT_ID", "CLIENT_SECRET", "ZUORA_ENV", "ORG_IDS", "PAYMENT_GATEWAY_ID", "PUBLISHABLE_KEY", "PROFILE_ID"};
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String k : requiredKeys) {
                String v = System.getenv(k);
                if (v == null) v = System.getProperty(k);
                if (v == null || v.trim().isEmpty()) missing.add(k);
            }
            if (!missing.isEmpty()) {
                System.err.println("Missing required configuration variables: " + String.join(", ", missing));
                System.err.println("Please provide them via environment variables or a .env file. Aborting startup.");
                System.exit(1);
            }

            // Map ZUORA_ENV to enum (only CSBX supported here)
            ZuoraClient.ZuoraEnv env = null;
            if (ZUORA_ENV_STR.equalsIgnoreCase("CSBX")) {
                env = ZuoraClient.ZuoraEnv.CSBX;
            } else {
                System.err.println("Unsupported ZUORA_ENV: " + ZUORA_ENV_STR + ". Only 'CSBX' is supported.");
                System.exit(1);
            }

            zuoraClient = new ZuoraClient(CLIENT_ID, CLIENT_SECRET, env);
    }


    public static void main(String[] args) {
        port(8888);
        staticFiles.externalLocation(Paths.get("public").toAbsolutePath().toString());
    String envOrgIds = System.getenv("ORG_IDS");
    if (envOrgIds == null) envOrgIds = System.getProperty("ORG_IDS");
    String envPaymentGateway = System.getenv("PAYMENT_GATEWAY_ID");
    if (envPaymentGateway == null) envPaymentGateway = System.getProperty("PAYMENT_GATEWAY_ID");
    final String paymentGatewayConfig = envPaymentGateway; // make effectively final for inner scopes
        if (envOrgIds != null && !envOrgIds.isEmpty()) {
            zuoraClient.setOrgIds(envOrgIds);
        } else {
            zuoraClient.setOrgIds("817afbb4-2a9a-4d83-bf35-5eeb3b6a6b25");
            System.out.println("Warning: ORG_IDS not set in environment; using default org id");
        }
        zuoraClient.initialize();
        zuoraClient.setDebugging(true);

        // Expose a simple unauthenticated endpoint so the frontend can obtain the publishable key/profile
        get("/config", (request, response) -> {
            response.type("application/json");
            String publishableKey = System.getenv("PUBLISHABLE_KEY");
            if (publishableKey == null) publishableKey = System.getProperty("PUBLISHABLE_KEY");
            String profileId = System.getenv("PROFILE_ID");
            if (profileId == null) profileId = System.getProperty("PROFILE_ID");
            if (profileId == null || profileId.isEmpty()) {
                profileId = "PF-00000002";
            }
            return gson.toJson(Map.of(
                "publishableKey", publishableKey == null ? "" : publishableKey,
                "profile", profileId
            ));
        });

        post("create-payment-session", (request, response) -> {
            response.type("application/json");
            Map<String, Object> map = gson.fromJson(request.body(), new TypeToken<Map<String, Object>>(){}.getType());
            String firstName = (String) map.get("firstName");
            String lastName = (String) map.get("lastName");
            String address = (String) map.get("address");
            String city = (String) map.get("city");
            String state = (String) map.get("state");
            String zip = (String) map.get("zip");
            String country = (String) map.get("country");
            String email = (String) map.get("email");
            String currency = (String) map.get("currency");
            String paymentMethodType = (String) map.get("paymentMethodType");
            String paymentGatewayId;

            // Prefer configured PAYMENT_GATEWAY_ID, fall back to hard-coded default
            if (paymentGatewayConfig != null && !paymentGatewayConfig.isEmpty()) {
                paymentGatewayId = paymentGatewayConfig;
            } else if ("creditcard".equals(paymentMethodType)) {
                paymentGatewayId = "8a8aa26697aeaa8b0197b17b1cde6ed6";
            } else {
                paymentGatewayId = "8a8aa26697aeaa8b0197b17b1cde6ed6";
            }

            final CreateAccountContact contact = new CreateAccountContact()
                    .firstName(firstName)
                    .lastName(lastName)
                    .address1(address)
                    .city(city)
                    .state(state)
                    .zipCode(zip)
                    .workEmail(email)
                    .country(country);
                    
            final CreateAccountRequest createAccountRequest = new CreateAccountRequest()
                    .name(String.join(" ", firstName, lastName))
                    .billToContact(contact)
                    .billCycleDay(0)
                    .soldToSameAsBillTo(true) 
                    .autoPay(false)
                    .currency(currency);

            final CreateAccountResponse createAccountResponse = zuoraClient.accountsApi().createAccountApi(createAccountRequest).execute();
           
            // Create payment session request
            final CreatePaymentSessionRequest createPaymentSessionRequest = new CreatePaymentSessionRequest()
                    .currency(currency)
                    .amount(new BigDecimal(0.01))
                    .processPayment(false)
                    .storePaymentMethod(true)
                    .accountId(createAccountResponse.getAccountId());

            if (StringUtils.isNotBlank(paymentGatewayId)) {
                createPaymentSessionRequest.setPaymentGateway(paymentGatewayId);
            }

            final CreatePaymentSessionResponse createPaymentSessionResponse = zuoraClient.paymentMethodsApi().createPaymentSessionApi(createPaymentSessionRequest).execute();

            // Log the full response to see available fields
            System.out.println("Payment session response: " + gson.toJson(createPaymentSessionResponse));
            
            return gson.toJson(createPaymentSessionResponse.getToken());
        });
    }
}