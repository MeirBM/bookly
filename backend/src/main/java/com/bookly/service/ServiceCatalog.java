package com.bookly.service;

import com.bookly.common.error.ApiException;
import com.bookly.service.dto.ServiceRequests.CreateService;
import com.bookly.service.dto.ServiceRequests.ServiceResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Services a business offers. Named ServiceCatalog so the type is not {@code ServiceService}. */
@Service
public class ServiceCatalog {

    private final ServiceOfferingRepository repository;

    public ServiceCatalog(ServiceOfferingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceResponse create(UUID businessId, CreateService request) {
        String name = request.name().trim();
        if (repository.existsByBusinessIdAndName(businessId, name)) {
            throw ApiException.conflict("SERVICE_NAME_TAKEN",
                    "That business already offers a service with this name.");
        }
        ServiceOffering saved = repository.save(new ServiceOffering(
                businessId, name, request.durationMinutes(), request.priceMinor()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(UUID businessId) {
        return repository.findByBusinessIdOrderByName(businessId).stream()
                .map(ServiceCatalog::toResponse)
                .toList();
    }

    /** Both ids, so a service belonging to another business cannot be reached by id. */
    @Transactional(readOnly = true)
    public ServiceOffering require(UUID businessId, UUID serviceId) {
        return repository.findByIdAndBusinessId(serviceId, businessId)
                .orElseThrow(() -> ApiException.notFoundInBusiness("SERVICE_NOT_FOUND",
                        "No such service in this business."));
    }

    /**
     * Deleting a service removes its employee links, which the {@code ON DELETE CASCADE} on
     * {@code employee_services} does — so no orphan row survives and criterion 2.19 holds without
     * application code remembering to tidy up.
     */
    @Transactional
    public void delete(UUID businessId, UUID serviceId) {
        repository.delete(require(businessId, serviceId));
    }

    private static ServiceResponse toResponse(ServiceOffering offering) {
        return new ServiceResponse(offering.getId(), offering.getName(),
                offering.getDurationMinutes(), offering.getPriceMinor());
    }
}
