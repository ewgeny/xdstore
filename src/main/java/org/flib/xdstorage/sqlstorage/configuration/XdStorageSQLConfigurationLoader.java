package org.flib.xdstorage.sqlstorage.configuration;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.postgresql.XdStoragePGDataSourceConfiguration;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XdStorageSQLConfigurationLoader {

    public static XdStorageSQLConfiguration load(final String filename) {
        final XdStorageSQLConfiguration configuration = new XdStorageSQLConfiguration();
        try {
            InputStream reader = ClassLoader.getSystemClassLoader().getResourceAsStream(filename);
            XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(reader);
            Object tmp = null;
            while (xmlReader.hasNext()) {
                switch (xmlReader.next()) {
                    case XMLStreamConstants.START_ELEMENT:
                        if (xmlReader.getLocalName().equals("dataSources")) {
                            readDataSourcesConfigurations(xmlReader, configuration);
                        } else if (xmlReader.getLocalName().equals("classesPolicies")) {
                            readClassesPoliciesConfigurations(xmlReader, configuration);
                        }
                        break;
                }
            }
        } catch (final Throwable cause) { // stupid quick solution
            throw new XdStorageIOException(cause);
        }
        return configuration;
    }

    private static void readDataSourcesConfigurations(final XMLStreamReader xmlReader, final XdStorageSQLConfiguration configuration) throws XMLStreamException {
        boolean stop = false;
        while (!stop) {
            switch (xmlReader.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    if (xmlReader.getLocalName().equals("dataSource")) {
                        readDataSourceConfiguration(xmlReader, configuration);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (xmlReader.getLocalName().equals("dataSources")) {
                        stop = true;
                    }
                    break;
            }
        }
    }

    private static void readDataSourceConfiguration(final XMLStreamReader xmlReader, final XdStorageSQLConfiguration configuration) throws XMLStreamException {
        final int count = xmlReader.getAttributeCount();
        String name = null, type = null;
        for (int i = 0; i < count; ++i) {
            final String attName = xmlReader.getAttributeLocalName(i);
            final String attValue = xmlReader.getAttributeValue(i);

            if (attName.equals("name")) {
                name = attValue;
            } else if (attName.equals("type")) {
                type = attValue;
            }
        }

        final XdStoragePGDataSourceConfiguration cfg = new XdStoragePGDataSourceConfiguration();
        cfg.setName(name);
        cfg.setType(XdStorageSQLDataSourceType.valueOf(type));

        final Map<String, String> settings = new HashMap<>();
        boolean stop = false;
        while (!stop) {
            switch (xmlReader.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    settings.put(xmlReader.getLocalName(), xmlReader.getElementText());
                    xmlReader.next();
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    stop = true;
                    break;
            }
        }

        settings.entrySet().stream().forEach(entry -> {
            if (entry.getKey().equals("server")) {
                cfg.setServer(entry.getValue());
            } else if (entry.getKey().equals("port")) {
                cfg.setPort(Integer.valueOf(entry.getValue()));
            } else if (entry.getKey().equals("database")) {
                cfg.setDatabase(entry.getValue());
            } else if (entry.getKey().equals("user")) {
                cfg.setUser(entry.getValue());
            } else if (entry.getKey().equals("password")) {
                cfg.setPassword(entry.getValue());
            } else if (entry.getKey().equals("initialConnections")) {
                cfg.setInitialConnections(Integer.valueOf(entry.getValue()));
            } else if (entry.getKey().equals("maxConnections")) {
                cfg.setMaxConnections(Integer.valueOf(entry.getValue()));
            }
        });

        configuration.addDataSourceConfig(cfg);
    }

    private static void readClassesPoliciesConfigurations(final XMLStreamReader xmlReader, final XdStorageSQLConfiguration configuration) throws XMLStreamException, ClassNotFoundException {
        while (xmlReader.hasNext()) {
            switch (xmlReader.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    if (xmlReader.getLocalName().equals("class")) {
                        readClassPolicyConfiguration(xmlReader, configuration);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    break;
            }
        }
    }

    private static void readClassPolicyConfiguration(final XMLStreamReader xmlReader, final XdStorageSQLConfiguration configuration) throws ClassNotFoundException, XMLStreamException {
        final int count = xmlReader.getAttributeCount();
        String name = null, dataSource = null, table = null, multiple = null, parentDataSource = null;
        for (int i = 0; i < count; ++i) {
            final String attName = xmlReader.getAttributeLocalName(i);
            final String attValue = xmlReader.getAttributeValue(i);

            if (attName.equals("name")) {
                name = attValue;
            } else if (attName.equals("multiple")) {
                multiple = attValue;
            } else if (attName.equals("parentDataSource")) {
                parentDataSource = attValue;
            } else if (attName.equals("dataSource")) {
                dataSource = attValue;
            } else if (attName.equals("table")) {
                table = attValue;
            }
        }

        boolean isMultiple = Boolean.parseBoolean(multiple);
        boolean isParentDataSource = Boolean.parseBoolean(parentDataSource);

        final XdStorageSQLClassConfiguration cfg = new XdStorageSQLClassConfiguration();
        cfg.setCl(Class.forName(name));
        cfg.setDataSource(dataSource);
        cfg.setTable(table);
        cfg.setMultiple(isMultiple);
        cfg.setParentDataSource(isParentDataSource);
        configuration.addClassConfig(cfg);

        if(isMultiple) {
            boolean stop = false;
            while (!stop) {
                switch (xmlReader.next()) {
                    case XMLStreamConstants.START_ELEMENT:
                        readRuleConfiguration(xmlReader, cfg);
                        xmlReader.next();
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        stop = true;
                        break;
                }
            }
        }
    }

    private static void readRuleConfiguration(final XMLStreamReader xmlReader, final XdStorageSQLClassConfiguration classConfiguration) {
        final int count = xmlReader.getAttributeCount();
        String type = null, dataSource = null, className = null;
        for (int i = 0; i < count; ++i) {
            final String attName = xmlReader.getAttributeLocalName(i);
            final String attValue = xmlReader.getAttributeValue(i);

            if (attName.equals("type")) {
                type = attValue;
            } else if (attName.equals("dataSource")) {
                dataSource = attValue;
            } else if (attName.equals("class")) {
                className = attValue;
            }
        }

        final XdStorageSQLRuleConfiguration ruleCfg = new XdStorageSQLRuleConfiguration();
        ruleCfg.setType(XdStorageSQLRuleType.valueOf(type));
        ruleCfg.setClassName(className);
        ruleCfg.setDataSource(dataSource);

        List<XdStorageSQLRuleConfiguration> rules = classConfiguration.getRules();
        if (rules == null) {
            classConfiguration.setRules(rules = new ArrayList<>());
        }
        rules.add(ruleCfg);
    }
}
