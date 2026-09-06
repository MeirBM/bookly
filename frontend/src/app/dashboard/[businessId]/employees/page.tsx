"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { use, useState } from "react";
import { AsyncSection } from "@/components/AsyncSection";
import { FormError } from "@/components/FormError";
import { AddEmployeeForm } from "@/components/employees/AddEmployeeForm";
import { ServicePicker } from "@/components/employees/ServicePicker";
import { WorkingHoursEditor } from "@/components/employees/WorkingHoursEditor";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

export default function EmployeesPage({
  params,
}: {
  params: Promise<{ businessId: string }>;
}) {
  const { businessId } = use(params);
  const { tokens } = useAuth();
  const token = tokens?.accessToken ?? "";
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string | null>(null);
  const [openEmployee, setOpenEmployee] = useState<string | null>(null);

  const employees = useQuery({
    queryKey: ["employees", businessId],
    queryFn: () => api.listEmployees(token, businessId),
    enabled: Boolean(token),
  });
  const services = useQuery({
    queryKey: ["services", businessId],
    queryFn: () => api.listServices(token, businessId),
    enabled: Boolean(token),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["employees", businessId] });
    void queryClient.invalidateQueries({ queryKey: ["setup", businessId] });
  };
  const onError = (error: unknown) =>
    setFailure(error instanceof ApiError ? error.body.message : "Could not reach the server.");

  const create = useMutation({
    mutationFn: (fullName: string) => api.createEmployee(token, businessId, { fullName }),
    onSuccess: () => {
      setFailure(null);
      invalidate();
    },
    onError,
  });

  const setServices = useMutation({
    mutationFn: ({ employeeId, serviceIds }: { employeeId: string; serviceIds: string[] }) =>
      api.setEmployeeServices(token, businessId, employeeId, serviceIds),
    onSuccess: invalidate,
    onError,
  });

  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        title="Employees"
        description="Availability is worked out per person, so each one needs the services they perform and the hours they work."
      />

      <FormError message={failure} />

      <section className="flex flex-col gap-4">
        <AsyncSection
          query={employees}
          label="employees"
          isEmpty={(data) => data.length === 0}
          empty={
            <>
              No employees yet. Nothing can be offered until at least one person exists, performs a
              service, and has working hours.
            </>
          }
        >
          {(data) => (
            <ul className="flex flex-col gap-3">
              {data.map((employee) => {
                const open = openEmployee === employee.id;
                return (
                  <li key={employee.id}>
                    <Card padded={false}>
                      <div className="flex items-center justify-between gap-4 px-5 py-4">
                        <div className="min-w-0">
                          <p className="truncate font-medium text-ink">{employee.fullName}</p>
                          <p className="mt-0.5 text-sm text-ink-subtle">
                            {employee.serviceIds.length === 0
                              ? "Performs nothing yet"
                              : `Performs ${employee.serviceIds.length} service${
                                  employee.serviceIds.length === 1 ? "" : "s"
                                }`}
                          </p>
                        </div>
                        <Button
                          variant="secondary"
                          size="sm"
                          type="button"
                          aria-expanded={open}
                          onClick={() => setOpenEmployee(open ? null : employee.id)}
                        >
                          {open ? "Hide" : "Services and hours"}
                        </Button>
                      </div>

                      {open ? (
                        <div className="flex flex-col gap-6 border-t border-border px-5 py-5">
                          <div>
                            <p className="text-sm font-medium text-ink">Performs</p>
                            <ServicePicker
                              employee={employee}
                              services={services.data}
                              disabled={setServices.isPending}
                              onToggle={(serviceIds) =>
                                setServices.mutate({ employeeId: employee.id, serviceIds })
                              }
                            />
                          </div>
                          <WorkingHoursEditor
                            businessId={businessId}
                            employeeId={employee.id}
                            token={token}
                            onFailure={onError}
                          />
                        </div>
                      ) : null}
                    </Card>
                  </li>
                );
              })}
            </ul>
          )}
        </AsyncSection>
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-ink">Add an employee</h2>
        <AddEmployeeForm pending={create.isPending} onAdd={(name) => create.mutate(name)} />
      </section>
    </div>
  );
}
