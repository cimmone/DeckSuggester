package com.decksuggester.decks;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchidektClientTests {

    @Test
    void createsNextDataDeckSlugs() {
        assertThat(ArchidektClient.slugify("Abilities Tribal"))
                .isEqualTo("abilities_tribal");
        assertThat(ArchidektClient.slugify("Rin & Seri: Friends"))
                .isEqualTo("rin_seri_friends");
    }

    @Test
    void parsesTheBuildIdFromTheNextDataScript() {
        String html = "<!doctype html><html><head></head><body>"
                + "<div id=\"__next\"></div>"
                + "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"props\":{},\"page\":\"/folders/[folderId]\","
                + "\"buildId\":\"oW1UyA2PrLFzwpb3vGHE8\"}"
                + "</script></body></html>";

        assertThat(ArchidektClient.parseBuildId(html, "https://archidekt.com/folders/95630"))
                .isEqualTo("oW1UyA2PrLFzwpb3vGHE8");
    }

    @Test
    void discoversTheBuildIdFromThePageBeforeRequestingNextData() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> page = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> json = mock(HttpResponse.class);
        when(page.statusCode()).thenReturn(200);
        when(page.body()).thenReturn(
                "<script id=\"__NEXT_DATA__\">{\"buildId\":\"runtime-build-42\"}</script>");
        when(json.statusCode()).thenReturn(200);
        when(json.body()).thenReturn("{\"rootFolder\":{\"id\":95630}}");
        when(http.send(any(HttpRequest.class),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(page, json);
        ArchidektClient client = new ArchidektClient(new ObjectMapper(), http,
                "https://archidekt.com", 100, 5000, 1, "");

        client.fetchFolder(95630);

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertThat(requests.getAllValues()).extracting(request -> request.uri().toString())
                .containsExactly(
                        "https://archidekt.com/folders/95630",
                        "https://archidekt.com/_next/data/runtime-build-42/folders/95630.json?folderId=95630");
    }
}
