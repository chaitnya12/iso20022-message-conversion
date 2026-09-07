package com.bank.iso20022converter.core;

import com.bank.iso20022converter.core.exception.MalformedMessageException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marshals/unmarshals the XJC-generated "Document" classes.
 *
 * <p>These generated classes are NOT annotated with {@code @XmlRootElement} (matching
 * the real ISO 20022 schemas) - the root element is only declared via each package's
 * {@code ObjectFactory}. So unmarshalling must target {@code JAXBElement<T>} and
 * marshalling must explicitly wrap the payload in a {@link JAXBElement} using the
 * message's namespace + "Document" local name. See {@code RoundTripSpikeTest} for
 * the spike that discovered this.
 */
@Component
public class JaxbMessageCodec {

    private final Map<Class<?>, JAXBContext> contextCache = new ConcurrentHashMap<>();

    public Object unmarshal(String xml, MessageTypeDescriptor descriptor) {
        try {
            Unmarshaller unmarshaller = contextFor(descriptor.documentClass()).createUnmarshaller();
            JAXBElement<?> element = unmarshaller.unmarshal(
                    new StreamSource(new StringReader(xml)), descriptor.documentClass());
            return element.getValue();
        } catch (JAXBException e) {
            throw new MalformedMessageException(
                    "Failed to parse XML as " + descriptor.messageId() + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public String marshal(Object document, MessageTypeDescriptor descriptor) {
        try {
            Marshaller marshaller = contextFor(descriptor.documentClass()).createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            JAXBElement element = new JAXBElement(
                    new QName(descriptor.namespaceUri(), MessageTypeDescriptor.ROOT_LOCAL_NAME),
                    descriptor.documentClass(),
                    document);
            StringWriter writer = new StringWriter();
            marshaller.marshal(element, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new MalformedMessageException(
                    "Failed to serialize " + descriptor.messageId() + " to XML: " + e.getMessage(), e);
        }
    }

    private JAXBContext contextFor(Class<?> documentClass) {
        return contextCache.computeIfAbsent(documentClass, cls -> {
            try {
                return JAXBContext.newInstance(cls);
            } catch (JAXBException e) {
                throw new IllegalStateException("Could not build JAXBContext for " + cls, e);
            }
        });
    }
}
