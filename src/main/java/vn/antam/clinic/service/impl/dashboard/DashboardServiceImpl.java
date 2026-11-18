package vn.antam.clinic.service.impl.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.antam.clinic.security.Roles;
import vn.antam.clinic.service.catalog.ServiceCatalogService;
import vn.antam.clinic.service.dashboard.DashboardService;
import vn.antam.clinic.service.dto.dashboard.DashboardSummary;
import vn.antam.clinic.service.organization.BedService;
import vn.antam.clinic.service.organization.DepartmentService;
import vn.antam.clinic.service.organization.RoomService;
import vn.antam.clinic.service.staff.StaffAccountService;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final DepartmentService departments;
    private final RoomService rooms;
    private final BedService beds;
    private final ServiceCatalogService services;
    private final StaffAccountService staff;

    public DashboardServiceImpl(
        DepartmentService departments,
        RoomService rooms,
        BedService beds,
        ServiceCatalogService services,
        StaffAccountService staff
    ) {
        this.departments = departments;
        this.rooms = rooms;
        this.beds = beds;
        this.services = services;
        this.staff = staff;
    }

    @Override
    @PreAuthorize(Roles.HAS_STAFF)
    public DashboardSummary summary() {
        return new DashboardSummary(
            departments.countActive(),
            rooms.countActive(),
            beds.countActive(),
            beds.countAvailable(),
            services.countActive(),
            staff.countActive()
        );
    }
}
