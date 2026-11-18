package vn.antam.clinic.security;

import java.util.List;

public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String RECEPTIONIST = "RECEPTIONIST";
    public static final String DOCTOR = "DOCTOR";
    public static final String NURSE = "NURSE";
    public static final String LAB_TECH = "LAB_TECH";
    public static final String PHARMACIST = "PHARMACIST";
    public static final String CASHIER = "CASHIER";
    public static final String PATIENT = "PATIENT";

    public static final String HAS_ADMIN = "hasRole('ADMIN')";
    public static final String HAS_STAFF =
        "hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR','NURSE','LAB_TECH','PHARMACIST','CASHIER')";
    public static final String IS_AUTHENTICATED = "isAuthenticated()";

    private static final List<String> STAFF = List.of(
        ADMIN,
        RECEPTIONIST,
        DOCTOR,
        NURSE,
        LAB_TECH,
        PHARMACIST,
        CASHIER
    );

    private Roles() {}

    public static boolean isStaffRole(String code) {
        return STAFF.contains(code);
    }

    public static List<String> staffCodes() {
        return STAFF;
    }
}
