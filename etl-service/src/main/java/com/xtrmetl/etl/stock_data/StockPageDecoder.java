package com.xtrmetl.etl.stock_data;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** FSC-specific wire anti-corruption boundary; XML never grants network authority. */
final class StockPageDecoder {
    private StockPageDecoder() { }

    /** Decode one size-bounded UTF-8 response without resolving external entities. */
    static DecodedPage decodePage(byte[] rawBody, FscStockDataSource.StockQuery sourceQuery, int pageNumber) {
        Element rootElement = readDocument(rawBody);
        requireName(rootElement, "response");
        Element headerElement = onlyChild(rootElement, "header");
        if (!"00".equals(textValue(onlyChild(headerElement, "resultCode")))) {
            throw new StockDataException("provider_rejected");
        }
        Element bodyElement = onlyChild(rootElement, "body");
        int responsePage = integerValue(textValue(onlyChild(bodyElement, "pageNo")));
        int responseSize = integerValue(textValue(onlyChild(bodyElement, "numOfRows")));
        int totalCount = integerValue(textValue(onlyChild(bodyElement, "totalCount")));
        if (responsePage != pageNumber || responseSize != sourceQuery.pageSize()) {
            throw new StockDataException("invalid_page");
        }
        List<StockPriceRecord> priceRecords = new ArrayList<>();
        Element itemsElement = onlyChild(bodyElement, "items");
        for (Element itemElement : elementChildren(itemsElement)) {
            requireName(itemElement, "item");
            if (priceRecords.size() >= sourceQuery.pageSize()) {
                throw new StockDataException("invalid_page");
            }
            priceRecords.add(decodeRecord(itemElement, sourceQuery, pageNumber));
        }
        return new DecodedPage(totalCount, List.copyOf(priceRecords));
    }

    private static Element readDocument(byte[] rawBody) {
        try {
            String xmlText = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawBody)).toString();
            if (xmlText.startsWith("\ufeff")) {
                xmlText = xmlText.substring(1);
            }
            DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newDefaultInstance();
            xmlFactory.setNamespaceAware(true);
            xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            xmlFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            xmlFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            xmlFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            xmlFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            xmlFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            xmlFactory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "8");
            xmlFactory.setXIncludeAware(false);
            xmlFactory.setExpandEntityReferences(false);
            var documentBuilder = xmlFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entity refused"); });
            documentBuilder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException failureValue) throws SAXException { throw failureValue; }
                @Override public void fatalError(SAXParseException failureValue) throws SAXException { throw failureValue; }
            });
            return documentBuilder.parse(new InputSource(new StringReader(xmlText))).getDocumentElement();
        } catch (CharacterCodingException failureValue) {
            throw new StockDataException("invalid_xml");
        } catch (Exception failureValue) {
            // XML/provider diagnostics may contain original values; they never cross this ACL.
            throw new StockDataException("invalid_xml");
        }
    }

    private static StockPriceRecord decodeRecord(Element itemElement, FscStockDataSource.StockQuery sourceQuery, int pageNumber) {
        Map<String, String> sourceFields = new LinkedHashMap<>();
        for (Element fieldElement : elementChildren(itemElement)) {
            requirePlainElement(fieldElement);
            String fieldName = fieldElement.getTagName();
            if (!fieldName.matches("[A-Za-z][A-Za-z0-9]{0,63}") || sourceFields.size() >= 64
                    || sourceFields.putIfAbsent(fieldName, textValue(fieldElement)) != null) {
                throw new StockDataException("invalid_record");
            }
        }
        try {
            String dateText = requiredField(sourceFields, "basDt");
            if (!dateText.matches("[0-9]{8}")) {
                throw new IllegalArgumentException();
            }
            LocalDate referenceDate = LocalDate.parse(dateText, DateTimeFormatter.BASIC_ISO_DATE);
            String shortCode = requiredField(sourceFields, "srtnCd");
            String isinCode = requiredField(sourceFields, "isinCd");
            if (!shortCode.matches("[A-Z0-9]{6,12}") || !isinCode.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]")
                    || referenceDate.isBefore(sourceQuery.fromDate()) || referenceDate.isAfter(sourceQuery.toDate())
                    || (sourceQuery.isinCode() != null && !sourceQuery.isinCode().equals(isinCode))) {
                throw new IllegalArgumentException();
            }
            BigDecimal openPrice = decimalValue(requiredField(sourceFields, "mkp"));
            BigDecimal highPrice = decimalValue(requiredField(sourceFields, "hipr"));
            BigDecimal lowPrice = decimalValue(requiredField(sourceFields, "lopr"));
            BigDecimal closePrice = decimalValue(requiredField(sourceFields, "clpr"));
            BigInteger tradingVolume = wholeValue(requiredField(sourceFields, "trqu"));
            BigInteger tradingValue = wholeValue(requiredField(sourceFields, "trPrc"));
            boolean noTradeRange = tradingVolume.signum() == 0 && openPrice.signum() == 0
                    && highPrice.signum() == 0 && lowPrice.signum() == 0;
            if (!noTradeRange && (lowPrice.compareTo(highPrice) > 0 || openPrice.compareTo(lowPrice) < 0
                    || openPrice.compareTo(highPrice) > 0 || closePrice.compareTo(lowPrice) < 0
                    || closePrice.compareTo(highPrice) > 0)) {
                throw new IllegalArgumentException();
            }
            if (tradingVolume.signum() > 0 && (openPrice.signum() == 0 || highPrice.signum() == 0
                    || lowPrice.signum() == 0 || closePrice.signum() == 0)) {
                throw new IllegalArgumentException();
            }
            return new StockPriceRecord(referenceDate, shortCode, isinCode,
                    requiredField(sourceFields, "itmsNm"), requiredField(sourceFields, "mrktCtg"),
                    openPrice, highPrice, lowPrice, closePrice, tradingVolume, tradingValue, sourceFields, pageNumber);
        } catch (RuntimeException failureValue) {
            throw new StockDataException("invalid_record");
        }
    }

    private static String requiredField(Map<String, String> sourceFields, String fieldName) {
        String fieldValue = sourceFields.get(fieldName);
        if (fieldValue == null || fieldValue.isBlank()) {
            throw new StockDataException("invalid_record");
        }
        return fieldValue;
    }

    private static BigDecimal decimalValue(String sourceValue) {
        if (sourceValue.length() > 38 || !sourceValue.matches("[0-9]+(?:\\.[0-9]+)?")) {
            throw new StockDataException("invalid_record");
        }
        return new BigDecimal(sourceValue);
    }

    private static BigInteger wholeValue(String sourceValue) {
        if (sourceValue.length() > 38 || !sourceValue.matches("[0-9]+")) {
            throw new StockDataException("invalid_record");
        }
        return new BigInteger(sourceValue);
    }

    private static int integerValue(String sourceValue) {
        try {
            if (!sourceValue.matches("[0-9]{1,9}")) {
                throw new NumberFormatException();
            }
            return Integer.parseInt(sourceValue);
        } catch (NumberFormatException failureValue) {
            throw new StockDataException("invalid_page");
        }
    }

    private static Element onlyChild(Element parentElement, String childName) {
        Element selectedElement = null;
        for (Element childElement : elementChildren(parentElement)) {
            if (childName.equals(childElement.getTagName())) {
                requirePlainElement(childElement);
                if (selectedElement != null) {
                    throw new StockDataException("invalid_xml");
                }
                selectedElement = childElement;
            }
        }
        if (selectedElement == null) {
            throw new StockDataException("invalid_xml");
        }
        return selectedElement;
    }

    private static List<Element> elementChildren(Element parentElement) {
        List<Element> childElements = new ArrayList<>();
        for (Node childNode = parentElement.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
            if (childNode instanceof Element childElement) {
                childElements.add(childElement);
            } else if ((childNode.getNodeType() == Node.TEXT_NODE || childNode.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !childNode.getTextContent().isBlank()) {
                throw new StockDataException("invalid_xml");
            }
        }
        return childElements;
    }

    private static String textValue(Element fieldElement) {
        StringBuilder fieldText = new StringBuilder();
        for (Node childNode = fieldElement.getFirstChild(); childNode != null; childNode = childNode.getNextSibling()) {
            if (childNode.getNodeType() != Node.TEXT_NODE && childNode.getNodeType() != Node.CDATA_SECTION_NODE) {
                throw new StockDataException("invalid_xml");
            }
            fieldText.append(childNode.getNodeValue());
            if (fieldText.length() > 1024) {
                throw new StockDataException("invalid_record");
            }
        }
        return fieldText.toString().strip();
    }

    private static void requireName(Element sourceElement, String expectedName) {
        requirePlainElement(sourceElement);
        if (!expectedName.equals(sourceElement.getTagName())) {
            throw new StockDataException("invalid_xml");
        }
    }

    private static void requirePlainElement(Element sourceElement) {
        if (sourceElement.getNamespaceURI() != null || sourceElement.hasAttributes()) {
            throw new StockDataException("invalid_xml");
        }
    }

    /** Internal decoded page; no completeness claim until collection finishes. */
    record DecodedPage(int totalCount, List<StockPriceRecord> priceRecords) { }
}
