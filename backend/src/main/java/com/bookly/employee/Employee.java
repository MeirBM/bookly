package com.bookly.employee;

import com.bookly.service.ServiceOffering;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Someone who performs services and has working hours.
 *
 * <p>Not the same thing as a {@code business_members} row, which is a login. Merging them would mean
 * every employee needs an account and no employee can be added before they accept an invitation —
 * turn-2 spec, pitfall 6.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** Unidirectional: services do not need to know who performs them. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "employee_services",
            joinColumns = @JoinColumn(name = "employee_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<ServiceOffering> services = new HashSet<>();

    protected Employee() {
        // for JPA
    }

    public Employee(UUID businessId, String fullName) {
        this.businessId = businessId;
        this.fullName = fullName;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<ServiceOffering> getServices() {
        return services;
    }

    public void replaceServices(Set<ServiceOffering> replacement) {
        services.clear();
        services.addAll(replacement);
    }
}
