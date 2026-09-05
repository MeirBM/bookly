package com.bookly.business;

/**
 * Membership roles turn 1 actually enforces.
 *
 * <p>MANAGER and SUPER_ADMIN appear in the brief but not here: their permissions are undefined, and
 * a declared role nobody checks is a claim rather than a control. See the out-of-scope list.
 */
public enum Role {
    BUSINESS_OWNER,
    EMPLOYEE
}
