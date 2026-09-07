package com.bank.iso20022converter.core;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.util.GregorianCalendar;

/** Small helper around {@link XMLGregorianCalendar}, needed because JAXB maps xs:date/xs:dateTime to it. */
public final class XmlDates {

    private static final DatatypeFactory FACTORY;

    static {
        try {
            FACTORY = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private XmlDates() {
    }

    public static XMLGregorianCalendar nowDateTime() {
        return FACTORY.newXMLGregorianCalendar(GregorianCalendar.from(
                java.time.ZonedDateTime.now()));
    }

    public static XMLGregorianCalendar fromLocalDateTime(LocalDateTime dateTime) {
        return FACTORY.newXMLGregorianCalendar(GregorianCalendar.from(dateTime.atZone(java.time.ZoneId.systemDefault())));
    }

    public static XMLGregorianCalendar copy(XMLGregorianCalendar source) {
        return source == null ? null : (XMLGregorianCalendar) source.clone();
    }
}
