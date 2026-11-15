package com.advertisingmarketplace.common.money;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class EcbExchangeRateProvider implements ExchangeRateProvider {

    private final RestClient restClient;
    private final ExchangeRateProperties properties;

    public EcbExchangeRateProvider(ExchangeRateProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public ExchangeRates fetchLatest() {
        byte[] xml = restClient.get().uri(properties.ecbUrl()).retrieve().body(byte[].class);
        if (xml == null || xml.length == 0) {
            throw new IllegalStateException("Empty response from " + properties.ecbUrl());
        }
        return parse(xml);
    }

    static ExchangeRates parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));

            LocalDate date = null;
            Map<String, BigDecimal> rates = new HashMap<>();
            NodeList cubes = document.getElementsByTagNameNS("*", "Cube");
            for (int i = 0; i < cubes.getLength(); i++) {
                Element cube = (Element) cubes.item(i);
                if (cube.hasAttribute("time")) {
                    date = LocalDate.parse(cube.getAttribute("time"));
                }
                if (cube.hasAttribute("currency") && cube.hasAttribute("rate")) {
                    BigDecimal rate = new BigDecimal(cube.getAttribute("rate"));
                    if (rate.signum() > 0) {
                        rates.put(cube.getAttribute("currency"), rate);
                    }
                }
            }
            if (date == null || rates.isEmpty()) {
                throw new IllegalStateException("No exchange rates found in ECB response");
            }
            return new ExchangeRates(date, Map.copyOf(rates));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse ECB exchange rates", e);
        }
    }
}
