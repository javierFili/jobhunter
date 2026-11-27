package com.jobhunter.jobsources.infrastructure;

import com.jobhunter.shared.http.SafeHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, hardened RSS 2.0 reader: DOCTYPE/external entities disabled (XXE), namespace-aware so custom elements
 * ({@code larajobs:salary}) are addressable by local name.
 */
final class RssSupport {

    private RssSupport() {
    }

    /** One {@code <item>}: child elements by local name (first occurrence wins), text content trimmed. */
    record Item(Map<String, String> fields) {
        String get(String localName) {
            String v = fields.get(localName);
            return v == null || v.isBlank() ? null : v;
        }
    }

    static List<Item> parse(SafeHttpClient.Response r, String source) {
        if (!r.ok()) throw new SafeHttpClient.HttpCallException(source + " responded HTTP " + r.status(), r.status(), null);
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(r.body().getBytes(StandardCharsets.UTF_8)));
            NodeList items = doc.getElementsByTagNameNS("*", "item");
            List<Item> out = new ArrayList<>();
            for (int i = 0; i < items.getLength(); i++) {
                Map<String, String> fields = new LinkedHashMap<>();
                NodeList children = items.item(i).getChildNodes();
                for (int c = 0; c < children.getLength(); c++) {
                    Node n = children.item(c);
                    if (n instanceof Element e) {
                        String name = e.getLocalName() == null ? e.getNodeName() : e.getLocalName();
                        fields.putIfAbsent(name, e.getTextContent() == null ? null : e.getTextContent().trim());
                    }
                }
                out.add(new Item(fields));
            }
            return out;
        } catch (Exception e) {
            throw new SafeHttpClient.HttpCallException(source + " returned invalid RSS", r.status(), e);
        }
    }

    /** RFC 1123 dates as used by RSS ("Wed, 25 Nov 2026 18:32:27 +0000"); null when unparseable. */
    static Instant rfc1123(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException e) {
            return HttpConnectorSupport.instant(s);
        }
    }
}
