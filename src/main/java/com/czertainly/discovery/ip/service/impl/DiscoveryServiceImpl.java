package com.czertainly.discovery.ip.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.IntegerAttributeContent;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.common.attribute.v2.properties.MetadataAttributeProperties;
import com.czertainly.api.model.connector.discovery.DiscoveryDataRequestDto;
import com.czertainly.api.model.connector.discovery.DiscoveryProviderDto;
import com.czertainly.api.model.connector.discovery.DiscoveryRequestDto;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.discovery.ip.dao.Certificate;
import com.czertainly.discovery.ip.dao.DiscoveryHistory;
import com.czertainly.discovery.ip.dto.ConnectionResponse;
import com.czertainly.discovery.ip.repository.CertificateRepository;
import com.czertainly.discovery.ip.service.ConnectionService;
import com.czertainly.discovery.ip.service.DiscoveryHistoryService;
import com.czertainly.discovery.ip.service.DiscoveryService;
import com.czertainly.discovery.ip.util.DiscoverIpHandler;
import com.czertainly.discovery.ip.util.X509ObjectToString;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServiceImpl.class);
    private ConnectionService connectionService;
    private CertificateRepository certificateRepository;
    private DiscoveryHistoryService discoveryHistoryService;

    @Autowired
    public void setConnectionService(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setDiscoveryHistoryService(DiscoveryHistoryService discoveryHistoryService) {
        this.discoveryHistoryService = discoveryHistoryService;
    }

    @Override
    public DiscoveryProviderDto getProviderDtoData(DiscoveryDataRequestDto request, DiscoveryHistory history) {
        DiscoveryProviderDto dto = new DiscoveryProviderDto();
        dto.setUuid(history.getUuid());
        dto.setName(history.getName());
        dto.setStatus(history.getStatus());
        dto.setMeta(AttributeDefinitionUtils.deserialize(history.getMeta(), MetadataAttribute.class));
        int totalCertificateSize = certificateRepository.findByDiscoveryId(history.getId()).size();
        dto.setTotalCertificatesDiscovered(totalCertificateSize);
        if (history.getStatus() == DiscoveryStatus.IN_PROGRESS) {
            dto.setCertificateData(new ArrayList<>());
            dto.setTotalCertificatesDiscovered(0);
        } else {
            Pageable page = PageRequest.of(request.getPageNumber() <= 0 ? 0 : request.getPageNumber() - 1, request.getItemsPerPage());
            dto.setCertificateData(certificateRepository.findAllByDiscoveryId(history.getId(), page).stream().map(Certificate::mapToDto).collect(Collectors.toList()));
        }
        return dto;
    }

    @Override
    public void deleteDiscovery(String uuid) throws NotFoundException {
        DiscoveryHistory discoveryHistory = discoveryHistoryService.getHistoryByUuid(uuid);
        List<Certificate> certificates = certificateRepository.findByDiscoveryId(discoveryHistory.getId());
        certificateRepository.deleteAll(certificates);
        discoveryHistoryService.deleteHistory(discoveryHistory);
    }

    @Override
    @Async
    public void discoverCertificate(DiscoveryRequestDto request, DiscoveryHistory history) throws NotFoundException {
        try {
            discoverCertificatesInternal(request, history);
        } catch (Exception e) {
            history.setStatus(DiscoveryStatus.FAILED);
            history.setMeta(AttributeDefinitionUtils.serialize(getReasonMeta(e.getMessage())));
            discoveryHistoryService.setHistory(history);
            logger.error(e.getMessage());
        }
    }

    public void discoverCertificatesInternal(DiscoveryRequestDto request, DiscoveryHistory history) {
        logger.info("Discovery initiated for the request with name {}", request.getName());
        List<String> urls = DiscoverIpHandler.getAllIp(request);
        List<String> successUrls = new ArrayList<>();
        List<String> failedUrls = new ArrayList<>();
        List<String> allCerts = new ArrayList<>();
        for (String url : urls) {
            logger.debug("Discovering certificate for " + url);
            try {
                ConnectionResponse connection = connectionService.getCertificates(url);
                logger.debug("Connection to the url success. Certificates obtained");
                X509Certificate[] certificates = connection.getCertificates();
                for (X509Certificate certificate : certificates) {
                    createCertificateEntry(certificate, history.getId(), url);
                    allCerts.add("Certificate");
                }
                successUrls.add(url);
            } catch (Exception e) {
                logger.error("Unable to connect to the URL " + url);
                logger.error(e.getMessage());
                failedUrls.add(url);
            }
        }
        logger.info("Discovery {} has total of {} certificates from {} sources", request.getName(), allCerts.size(), urls.size());
        history.setStatus(DiscoveryStatus.COMPLETED);
        history.setMeta(AttributeDefinitionUtils.serialize(getDiscoveryMetadata(urls.size(), successUrls.size(), failedUrls.size())));
        discoveryHistoryService.setHistory(history);
        logger.info("Discovery Completed. Name of the discovery is {}", request.getName());
    }

    private void createCertificateEntry(X509Certificate certificate, Long discoveryId, String discoverySource) {
        Certificate cert = new Certificate();
        String base64Content = X509ObjectToString.toPem(certificate);
        if (certificateRepository.findByDiscoveryIdAndBase64Content(discoveryId, base64Content).isEmpty()) {
            cert.setDiscoveryId(discoveryId);
            cert.setMeta(AttributeDefinitionUtils.serialize(getCertificateMetadata(discoverySource)));
            cert.setBase64Content(base64Content);
            cert.setUuid(UUID.randomUUID().toString());
            certificateRepository.save(cert);
        }
    }

    private List<MetadataAttribute> getDiscoveryMetadata(Integer totalUrls, Integer successUrls, Integer failedUrls) {
        List<MetadataAttribute> attributes = new ArrayList<>();

        //Total URL
        MetadataAttribute totalAttribute = new MetadataAttribute();
        totalAttribute.setName("totalUrls");
        totalAttribute.setUuid("872ca286-601f-11ed-9b6a-0242ac120002");
        totalAttribute.setContentType(AttributeContentType.INTEGER);
        totalAttribute.setType(AttributeType.META);
        totalAttribute.setDescription("Total number of URLs for the discovery");

        MetadataAttributeProperties totalAttributeProperties = new MetadataAttributeProperties();
        totalAttributeProperties.setLabel("Total URLs");
        totalAttributeProperties.setVisible(true);

        totalAttribute.setProperties(totalAttributeProperties);
        totalAttribute.setContent(List.of(new IntegerAttributeContent(totalUrls.toString(), totalUrls)));
        attributes.add(totalAttribute);

        //Success URL
        MetadataAttribute successAttribute = new MetadataAttribute();
        successAttribute.setName("successUrls");
        successAttribute.setUuid("872ca600-601f-11ed-9b6a-0242ac120002");
        successAttribute.setContentType(AttributeContentType.INTEGER);
        successAttribute.setType(AttributeType.META);
        successAttribute.setDescription("Successful certificate discovery URLs");

        MetadataAttributeProperties successAttributeProperties = new MetadataAttributeProperties();
        successAttributeProperties.setLabel("No Of Success URLs");
        successAttributeProperties.setVisible(true);

        successAttribute.setProperties(successAttributeProperties);
        successAttribute.setContent(List.of(new IntegerAttributeContent(successUrls.toString(), successUrls)));
        attributes.add(successAttribute);

        //Failed URL
        MetadataAttribute failedAttribute = new MetadataAttribute();
        failedAttribute.setName("failedUrls");
        failedAttribute.setUuid("872ca7ea-601f-11ed-9b6a-0242ac120002");
        failedAttribute.setContentType(AttributeContentType.INTEGER);
        failedAttribute.setType(AttributeType.META);
        failedAttribute.setDescription("Failed certificate discovery URLs");

        MetadataAttributeProperties failedAttributeProperties = new MetadataAttributeProperties();
        failedAttributeProperties.setLabel("No Of Failed URLs");
        failedAttributeProperties.setVisible(true);

        failedAttribute.setProperties(failedAttributeProperties);
        failedAttribute.setContent(List.of(new IntegerAttributeContent(failedUrls.toString(), failedUrls)));
        attributes.add(failedAttribute);

        return attributes;
    }

    private List<MetadataAttribute> getCertificateMetadata(String discoverySource) {
        List<MetadataAttribute> attributes = new ArrayList<>();

        //Total URL
        MetadataAttribute attribute = new MetadataAttribute();
        attribute.setName("discoverySource");
        attribute.setUuid("000043aa-6022-11ed-9b6a-0242ac120002");
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setType(AttributeType.META);
        attribute.setDescription("Source from where the certificate is discovered");

        MetadataAttributeProperties attributeProperties = new MetadataAttributeProperties();
        attributeProperties.setLabel("Discovery Source");
        attributeProperties.setVisible(true);
        attributeProperties.setGlobal(true);

        attribute.setProperties(attributeProperties);
        attribute.setContent(List.of(new StringAttributeContent(discoverySource, discoverySource)));
        attributes.add(attribute);

        return attributes;
    }

    private List<MetadataAttribute> getReasonMeta(String exception) {
        List<MetadataAttribute> attributes = new ArrayList<>();

        //Exception Reason
        MetadataAttribute attribute = new MetadataAttribute();
        attribute.setName("reason");
        attribute.setUuid("abc0412a-60f6-11ed-9b6a-0242ac120002");
        attribute.setContentType(AttributeContentType.STRING);
        attribute.setType(AttributeType.META);
        attribute.setDescription("Reason for failure");

        MetadataAttributeProperties attributeProperties = new MetadataAttributeProperties();
        attributeProperties.setLabel("Reason");
        attributeProperties.setVisible(true);

        attribute.setProperties(attributeProperties);
        attribute.setContent(List.of(new StringAttributeContent(exception)));
        attributes.add(attribute);

        return attributes;
    }

}