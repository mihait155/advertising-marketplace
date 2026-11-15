package com.advertisingmarketplace.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.common.money.ExchangeRateService;
import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.advertisingmarketplace.support.FakeEcbServer;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PostingPayFilterIT extends AbstractIntegrationTest {

    @Autowired
    private FakeEcbServer ecb;

    @Autowired
    private ExchangeRateService exchangeRateService;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        ecb.respondWithFixture();
        assertThat(exchangeRateService.refresh()).isTrue();
        token = accessToken(signup("Jane Streamer", "jane@example.com", "MARKETER"));
        createPosting("A 1000-2000 EUR", "1000", "2000", "EUR");
        createPosting("B 1000-1500 USD", "1000", "1500", "USD");
        createPosting("C 500-600 GBP", "500", "600", "GBP");
        createPosting("D from 4500 RON", "4500", null, "RON");
        createPosting("E no pay", null, null, null);
        createPosting("F up to 300 EUR", null, "300", "EUR");
    }

    private void createPosting(String title, String payFrom, String payTo, String currency) throws Exception {
        String pay = currency == null ? "" : ",\"payFrom\":%s,\"payTo\":%s,\"payCurrency\":\"%s\""
                .formatted(payFrom, payTo, currency);
        mockMvc.perform(bearer(post("/api/v1/postings"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MARKETER_POSTING\",\"status\":\"OPEN\",\"title\":\"%s\",\"text\":\"x\"%s}"
                                .formatted(title, pay)))
                .andExpect(status().isCreated());
    }

    private List<String> titles(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(bearer(request, token).param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> all = JsonPath.read(body, "$.content[*].title");
        return all.stream().map(title -> title.substring(0, 1)).toList();
    }

    @Test
    void filtersByOverlappingPayRangeAcrossCurrencies() throws Exception {
        assertThat(titles(get("/api/v1/postings").param("minPay", "1000")))
                .containsExactlyInAnyOrder("A", "B", "D");
        assertThat(titles(get("/api/v1/postings").param("maxPay", "700")))
                .containsExactlyInAnyOrder("C", "F");
        assertThat(titles(get("/api/v1/postings").param("minPay", "700").param("maxPay", "850")))
                .containsExactlyInAnyOrder("B", "C");
    }

    @Test
    void filterAmountsCanBeGivenInAnyCurrency() throws Exception {
        assertThat(titles(get("/api/v1/postings").param("minPay", "1250").param("currency", "USD")))
                .containsExactlyInAnyOrder("A", "B", "D");
        assertThat(titles(get("/api/v1/postings").param("maxPay", "3500").param("currency", "ron")))
                .containsExactlyInAnyOrder("C", "F");
    }

    @Test
    void sortsByPayAcrossCurrencies() throws Exception {
        assertThat(titles(get("/api/v1/postings").param("sort", "pay,desc")))
                .containsExactly("A", "B", "D", "C", "F", "E");
        assertThat(titles(get("/api/v1/postings").param("sort", "pay,asc")))
                .containsExactly("F", "C", "B", "D", "A", "E");
    }

    @Test
    void newRatesRecomputeExistingPostings() throws Exception {
        ecb.respondWith(200, """
                <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01"
                                 xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
                  <Cube><Cube time="2026-11-04">
                    <Cube currency="USD" rate="2.0"/><Cube currency="GBP" rate="0.8"/><Cube currency="RON" rate="5"/>
                  </Cube></Cube>
                </gesmes:Envelope>
                """);

        assertThat(exchangeRateService.refresh()).isTrue();

        assertThat(titles(get("/api/v1/postings").param("minPay", "1000")))
                .containsExactlyInAnyOrder("A", "D");
        mockMvc.perform(bearer(get("/api/v1/currencies"), token))
                .andExpect(jsonPath("$[1].code").value("USD"))
                .andExpect(jsonPath("$[1].unitsPerEur").value(2))
                .andExpect(jsonPath("$[1].rateDate").value("2026-11-04"));
    }

    @Test
    void failedRefreshKeepsPreviousRates() throws Exception {
        ecb.respondWith(503, "maintenance");

        assertThat(exchangeRateService.refresh()).isFalse();

        assertThat(titles(get("/api/v1/postings").param("minPay", "1000")))
                .containsExactlyInAnyOrder("A", "B", "D");
        mockMvc.perform(bearer(get("/api/v1/currencies"), token))
                .andExpect(jsonPath("$[1].unitsPerEur").value(1.25))
                .andExpect(jsonPath("$[1].rateDate").value("2026-11-03"));
    }

    @Test
    void invalidFilterAndSortParametersAreRejected() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/postings"), token).param("minPay", "500").param("maxPay", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("minPay must not be greater than maxPay"));
        mockMvc.perform(bearer(get("/api/v1/postings"), token).param("currency", "JPY").param("minPay", "1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(bearer(get("/api/v1/postings"), token).param("sort", "creator.passwordHash"))
                .andExpect(status().isBadRequest());
    }
}
