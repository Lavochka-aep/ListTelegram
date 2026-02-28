package com.lavochk.listTelegram;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Crypto Pay API integration for accepting cryptocurrency payments.
 * Documentation: https://help.send.tg/en/articles/10279948-crypto-pay-api
 */
public class CryptoPayAPI {

    private final ListTelegram plugin;
    private final String apiToken;
    private final String baseUrl;
    private boolean isEnabled = false;

    public CryptoPayAPI(ListTelegram plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        
        this.apiToken = config.getString("crypto-pay.token", "");
        this.baseUrl = config.getString("crypto-pay.api-url", "https://pay.crypt.bot/api");
        
        // Check if token is configured
        if (apiToken != null && !apiToken.isEmpty() && !apiToken.equals("YOUR_CRYPTO_PAY_TOKEN")) {
            this.isEnabled = true;
            plugin.getLogger().info("Crypto Pay API is enabled.");
        } else {
            plugin.getLogger().warning("Crypto Pay token not configured. Cryptocurrency payments will be disabled.");
        }
    }

    /**
     * Check if Crypto Pay is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Create a new invoice for payment
     * @param amount Amount in the specified currency
     * @param currency Currency code (USDT, BTC, ETH, etc.)
     * @param description Invoice description
     * @return Invoice URL if successful, null otherwise
     */
    public String createInvoice(double amount, String currency, String description) {
        if (!isEnabled) {
            return null;
        }

        try {
            // Используем asset вместо currency
            String jsonBody = String.format(
                "{\"asset\": \"%s\", \"amount\": \"%.2f\", \"description\": \"%s\", \"expires_in\": 3600}",
                currency, amount, escapeJson(description)
            );

            String response = makeRequest("/createInvoice", jsonBody);
            
            plugin.getLogger().info("Crypto Pay response: " + response);
            
            if (response != null && response.contains("pay_url")) {
                // Parse the pay_url from response
                int startIndex = response.indexOf("\"pay_url\":\"") + 11;
                int endIndex = response.indexOf("\"", startIndex);
                if (startIndex > 10 && endIndex > startIndex) {
                    return response.substring(startIndex, endIndex);
                }
            }
            
            plugin.getLogger().warning("Failed to create Crypto Pay invoice: " + response);
        } catch (Exception e) {
            plugin.getLogger().warning("Error creating Crypto Pay invoice: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Create a simple invoice with default settings
     * @param amountUSD Amount in USD (will be converted to available crypto)
     * @param description Payment description
     * @return Invoice URL
     */
    public String createDonateInvoice(double amountUSD, String description) {
        if (!isEnabled) {
            return null;
        }

        try {
            // Сначала получаем доступные валюты
            String currenciesJson = makeRequest("/getCurrencies", "{}");
            plugin.getLogger().info("Available currencies: " + currenciesJson);
            
            // Ищем USDT в ответе
            String assetCode = "USDT";
            if (currenciesJson != null && currenciesJson.contains("USDT")) {
                assetCode = "USDT";
            } else if (currenciesJson != null && currenciesJson.contains("USDTE")) {
                assetCode = "USDTE";
            } else if (currenciesJson != null && currenciesJson.contains("USDT_MV")) {
                assetCode = "USDT_MV";
            }
            
            // Создаём invoice
            String jsonBody = String.format(
                "{\"asset\": \"%s\", \"amount\": \"%.2f\", \"description\": \"%s\", \"expires_in\": 3600}",
                assetCode, amountUSD, escapeJson(description)
            );

            plugin.getLogger().info("Creating Crypto Pay invoice: " + jsonBody);
            
            String response = makeRequest("/createInvoice", jsonBody);
            
            plugin.getLogger().info("Crypto Pay response: " + response);
            
            if (response != null && response.contains("pay_url")) {
                int startIndex = response.indexOf("\\u0022pay_url\\u0022:\\u0022") + 28;
                int endIndex = response.indexOf("\\u0022", startIndex);
                if (startIndex > 27 && endIndex > startIndex) {
                    String url = response.substring(startIndex, endIndex);
                    return url.replace("\\u0022", "\"").replace("\\/", "/");
                }
                // Alternative parsing
                startIndex = response.indexOf("pay_url\":\"") + 10;
                endIndex = response.indexOf("\"", startIndex);
                if (startIndex > 9 && endIndex > startIndex) {
                    return response.substring(startIndex, endIndex);
                }
            }
            
            plugin.getLogger().warning("Failed to create donate invoice: " + response);
        } catch (Exception e) {
            plugin.getLogger().warning("Error creating donate invoice: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    /**
     * Get available currencies and exchange rates
     */
    public String getCurrencies() {
        if (!isEnabled) {
            return null;
        }
        return makeRequest("/getCurrencies", "{}");
    }

    /**
     * Get balance of the Crypto Pay account
     */
    public String getBalance() {
        if (!isEnabled) {
            return null;
        }
        return makeRequest("/getBalance", "{}");
    }

    /**
     * Make an API request to Crypto Pay
     */
    private String makeRequest(String endpoint, String jsonBody) {
        try {
            URL url = new URL(baseUrl + endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Crypto-Pay-API-Token", apiToken);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return response.toString();
                }
            } else {
                // Читаем error stream для получения更多信息
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line.trim());
                    }
                    plugin.getLogger().warning("Crypto Pay API error " + responseCode + ": " + errorResponse.toString());
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Crypto Pay request failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Escape special characters for JSON
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
