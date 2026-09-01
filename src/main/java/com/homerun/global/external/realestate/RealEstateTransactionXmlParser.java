package com.homerun.global.external.realestate;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
class RealEstateTransactionXmlParser {

    ParsedResponse parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            String resultCode = firstText(document, "resultCode", "returnReasonCode");
            String resultMessage = firstText(document, "resultMsg", "returnAuthMsg", "errMsg");
            int totalCount = parseInteger(firstText(document, "totalCount"));

            List<Map<String, String>> items = new ArrayList<>();
            NodeList itemNodes = document.getElementsByTagName("item");
            for (int index = 0; index < itemNodes.getLength(); index++) {
                items.add(toMap(itemNodes.item(index)));
            }

            return new ParsedResponse(resultCode, resultMessage, totalCount, List.copyOf(items));
        } catch (Exception exception) {
            throw new RealEstateTransactionUpstreamException("실거래가 API XML 응답을 해석하지 못했습니다.", exception);
        }
    }

    private Map<String, String> toMap(Node itemNode) {
        Map<String, String> values = new LinkedHashMap<>();
        NodeList children = itemNode.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                values.put(child.getNodeName(), child.getTextContent().trim());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private String firstText(Document document, String... tagNames) {
        for (String tagName : tagNames) {
            NodeList nodes = document.getElementsByTagName(tagName);
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent().trim();
            }
        }
        return "";
    }

    private int parseInteger(String value) {
        if (value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new RealEstateTransactionUpstreamException("실거래가 API의 전체 건수가 숫자가 아닙니다.", exception);
        }
    }

    record ParsedResponse(String resultCode, String resultMessage, int totalCount, List<Map<String, String>> items) {}
}
