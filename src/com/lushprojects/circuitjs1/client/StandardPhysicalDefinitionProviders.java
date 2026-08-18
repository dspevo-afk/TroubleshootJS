package com.lushprojects.circuitjs1.client;

import java.util.HashMap;

/** Registry of the currently supported typed physical-definition adapters. */
final class StandardPhysicalDefinitionProviders {
    static final PhysicalDefinitionProvider<ResistorNameplate> RESISTOR =
        new PhysicalDefinitionProvider<ResistorNameplate>() {
            public String getProviderId() { return "RESISTOR"; }
            public void add(BoardPhysicalSpecifications definitions,
                    ResistorNameplate specification) {
                if (specification == null)
                    throw new IllegalArgumentException("Missing resistor specification");
                definitions.addPhysicalDefinition(specification.getComponentId(), specification,
                    new PhysicalNameplate(specification.getComponentId(),
                        "Physical resistor markings", "Markings", "Color bands"),
                    PhysicalPackages.AXIAL_RESISTOR);
            }
            public ResistorNameplate find(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                return value instanceof ResistorNameplate ? (ResistorNameplate) value : null;
            }
            public ResistorNameplate require(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                if (value == null) throw missing(componentId, getProviderId());
                if (!(value instanceof ResistorNameplate))
                    throw wrongType(componentId, getProviderId());
                return (ResistorNameplate) value;
            }
        };

    static final PhysicalDefinitionProvider<DiodeNameplate> DIODE =
        new PhysicalDefinitionProvider<DiodeNameplate>() {
            public String getProviderId() { return "DIODE"; }
            public void add(BoardPhysicalSpecifications definitions,
                    DiodeNameplate specification) {
                if (specification == null)
                    throw new IllegalArgumentException("Missing diode specification");
                definitions.addPhysicalDefinition(specification.getComponentId(), specification,
                    new PhysicalNameplate(specification.getComponentId(),
                        specification.getDisplayName()), PhysicalPackages.AXIAL_DIODE);
            }
            public DiodeNameplate find(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                return value instanceof DiodeNameplate ? (DiodeNameplate) value : null;
            }
            public DiodeNameplate require(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                if (value == null) throw missing(componentId, getProviderId());
                if (!(value instanceof DiodeNameplate))
                    throw wrongType(componentId, getProviderId());
                return (DiodeNameplate) value;
            }
        };

    static final PhysicalDefinitionProvider<LedNameplate> LED =
        new PhysicalDefinitionProvider<LedNameplate>() {
            public String getProviderId() { return "LED"; }
            public void add(BoardPhysicalSpecifications definitions, LedNameplate specification) {
                if (specification == null)
                    throw new IllegalArgumentException("Missing LED specification");
                definitions.addPhysicalDefinition(specification.getComponentId(), specification,
                    new PhysicalNameplate(specification.getComponentId(),
                        specification.getDisplayName()), PhysicalPackages.THROUGH_HOLE_LED);
            }
            public LedNameplate find(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                return value instanceof LedNameplate ? (LedNameplate) value : null;
            }
            public LedNameplate require(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                if (value == null) throw missing(componentId, getProviderId());
                if (!(value instanceof LedNameplate))
                    throw wrongType(componentId, getProviderId());
                return (LedNameplate) value;
            }
        };

    static final PhysicalDefinitionProvider<CapacitorSpecification> CAPACITOR =
        new PhysicalDefinitionProvider<CapacitorSpecification>() {
            public String getProviderId() { return "CAPACITOR"; }
            public void add(BoardPhysicalSpecifications definitions,
                    CapacitorSpecification specification) {
                if (specification == null)
                    throw new IllegalArgumentException("Missing capacitor specification");
                definitions.addPhysicalDefinition(specification.getSpecificationId(), specification,
                    specification.getNameplate().forPhysicalPartId(
                        specification.getSpecificationId()), specification.getPhysicalPackage());
            }
            public CapacitorSpecification find(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                return value instanceof CapacitorSpecification ? (CapacitorSpecification) value : null;
            }
            public CapacitorSpecification require(BoardPhysicalSpecifications definitions,
                    String componentId) {
                PhysicalSpecification value = definitions.getSpecification(componentId);
                if (value == null) throw missing(componentId, getProviderId());
                if (!(value instanceof CapacitorSpecification))
                    throw wrongType(componentId, getProviderId());
                return (CapacitorSpecification) value;
            }
        };

    private static final HashMap<String, PhysicalDefinitionProvider<?>> PROVIDERS =
        new HashMap<String, PhysicalDefinitionProvider<?>>();

    static {
        register(RESISTOR);
        register(DIODE);
        register(LED);
        register(CAPACITOR);
    }

    private StandardPhysicalDefinitionProviders() {}

    static PhysicalDefinitionProvider<?> get(String providerId) {
        return PROVIDERS.get(providerId);
    }

    private static void register(PhysicalDefinitionProvider<?> provider) {
        if (PROVIDERS.put(provider.getProviderId(), provider) != null)
            throw new IllegalStateException("Duplicate physical definition provider: " +
                provider.getProviderId());
    }

    private static IllegalStateException wrongType(String componentId, String providerId) {
        return new IllegalStateException("Physical definition is not a " + providerId +
            " specification: " + componentId);
    }

    private static IllegalStateException missing(String componentId, String providerId) {
        return new IllegalStateException("Missing " + providerId +
            " physical specification: " + componentId);
    }
}
