package com.bank.iso20022converter.core;

import com.bank.iso20022converter.core.exception.MalformedMessageException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

/** Cheaply reads just the root element's namespace URI, for message-type auto-detection. */
public final class XmlNamespaceSniffer {

    private XmlNamespaceSniffer() {
    }

    public static String rootNamespace(String xml) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // XXE hardening: this reader only ever peeks at the root start element, but
        // disable external entity/DTD resolution defensively regardless.
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        String ns = reader.getNamespaceURI();
                        if (ns == null) {
                            throw new MalformedMessageException(
                                    "Root element has no namespace; cannot auto-detect message type.");
                        }
                        return ns;
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new MalformedMessageException("Failed to parse XML: " + e.getMessage(), e);
        }
        throw new MalformedMessageException("No root element found in XML.");
    }
}
