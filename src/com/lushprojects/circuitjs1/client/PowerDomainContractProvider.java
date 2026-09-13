package com.lushprojects.circuitjs1.client;

/** Immutable power/reference declarations consumed by generation identity. */
interface PowerDomainContractProvider extends PhysicalBoardRuntimeCapability {
    PowerDomainContract getContract();
}
