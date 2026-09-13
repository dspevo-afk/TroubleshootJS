package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Relay-specific five-terminal adapter over the A08 mutation transaction. */
final class ReplaceableRelayCapability implements PhysicalBoardRuntimeCapability, PhysicalBoardInstallationProvider.Scoped, WorkbenchPartsProvider {
    static final String ID="REPLACEABLE_RELAY";
    static final String COIL_5V="RELAY_5V", COIL_12V="RELAY_12V";
    private final RelaySlot slot;
    private final PhysicalPartInventory<PhysicalRelayPart> inventory;
    ReplaceableRelayCapability(PhysicalBoardSlot physical,PhysicalRelayPart original,WireElm[] attachments) {
        slot=new RelaySlot(physical,original,attachments);
        inventory=new PhysicalPartInventory<PhysicalRelayPart>(physical.getRuntime(),physical.getComponentId()+"_RELAYS",PhysicalRelayPart.class);
        inventory.add(original);
    }
    public String getCapabilityId() { return ID; }
    public String getComponentId() { return slot.getComponentId(); }
    public PhysicalMutationSlot getMutationSlot() { return slot; }
    public PhysicalPartInventory<?> getMutationInventory() { return inventory; }
    public String getCatalogTitle() { return "Relay Replacement Catalog"; }
    public String getInstallNewLabel() { return "Install new relay"; }
    public boolean showOccupiedMessageWhenPowered() { return false; }
    public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
        Vector<WorkbenchCatalogEntry> out=new Vector<WorkbenchCatalogEntry>();
        out.add(new WorkbenchCatalogEntry(COIL_5V,new RelaySpecification(5).label()));
        out.add(new WorkbenchCatalogEntry(COIL_12V,new RelaySpecification(12).label())); return out;
    }
    public Vector<PhysicalPart<?>> getLooseParts() {
        Vector<PhysicalPart<?>> out=new Vector<PhysicalPart<?>>();out.addAll(inventory.getLooseParts());return out;
    }
    public String getPartLabel(PhysicalPart<?> part) {
        if(part==null || !inventory.contains(part.getId()) || inventory.get(part.getId())!=part)
            throw new IllegalArgumentException("Foreign relay inventory part");
        return part.getPlayerVisibleNameplate().getWorkbenchDetailValue();
    }
    public PhysicalPart<?> getPart(String id) { return inventory.get(id); }
    public boolean ownsPart(String id) { return inventory.contains(id); }
    public PhysicalSlotMutationProvider install(CirSim sim,GeneratedBoardInstance owner,
            BoardModificationController modifications,double time) {
        if(owner.getPhysicalBoardRuntime()!=slot.physical.getRuntime()) throw new IllegalArgumentException("Foreign relay installation");
        return new Controller(sim,owner,modifications);
    }
    private final class Controller implements PhysicalSlotMutationProvider,PhysicalSlotMutationProvider.Scoped {
        private final CirSim sim;
        private final GeneratedBoardInstance owner;
        private final BoardModificationController modifications;
        Controller(CirSim sim,GeneratedBoardInstance owner,BoardModificationController modifications) {
            this.sim=sim;this.owner=owner;this.modifications=modifications;
        }
        public String getComponentId(){return slot.getComponentId();}
        public PhysicalMutationSlot getMutationSlot(){return slot;}
        public boolean ownsPart(String id){return inventory.contains(id);}
        public WorkbenchCapabilityMetadata getMetadata(){return new WorkbenchCapabilityMetadata(ID,"Relay workbench","SLOT_OPERATIONS");}
        public String getOperationLabel(WorkbenchOperation op){return WorkbenchOperation.REMOVE.equals(op.getId())?"Remove component":
            WorkbenchOperation.INSTALL.equals(op.getId())?"Install as "+getComponentId():"Install new relay";}
        public boolean supports(WorkbenchOperation op){return op!=null && getComponentId().equals(op.getComponentId()) &&
            (WorkbenchOperation.REMOVE.equals(op.getId())||WorkbenchOperation.INSTALL.equals(op.getId())||WorkbenchOperation.CATALOG_INSTALL.equals(op.getId()));}
        private boolean safe(){return sim.getGeneratedBoardInstance()==owner && sim.isChallengeInteractionEnabled() &&
            !sim.activeMeasurementOverlay && sim.getBoardPowerController().isElectricallyUnpowered() &&
            !owner.getPhysicalBoardRuntime().isMutationInProgress() && !owner.getPhysicalBoardRuntime().isMutationQuarantined() &&
            RelayOutputBehavior.isDischarged(owner);}
        public boolean isAvailable(WorkbenchOperation op,WorkbenchCapabilityContext context){
            if(!supports(op)||!safe())return false;
            if(WorkbenchOperation.REMOVE.equals(op.getId())) return !slot.isEmpty() && (op.getPart()==null||op.getPart()==slot.getInstalledPart());
            if(!slot.isEmpty())return false;
            if(WorkbenchOperation.CATALOG_INSTALL.equals(op.getId()))return COIL_5V.equals(op.getCatalogEntryId())||COIL_12V.equals(op.getCatalogEntryId());
            return op.getPart()!=null && inventory.contains(op.getPart().getId()) && inventory.get(op.getPart().getId())==op.getPart() && !op.getPart().isInstalled();
        }
        public boolean invoke(WorkbenchOperation op,WorkbenchCapabilityContext context){
            if(!isAvailable(op,context))return false;
            if(WorkbenchOperation.REMOVE.equals(op.getId()))return removeInstalledPart();
            if(WorkbenchOperation.INSTALL.equals(op.getId()))return install(op.getPart().getId());
            return installNewFromCatalog(op.getCatalogEntryId());
        }
        public boolean removeInstalledPart(){return mutate("remove",null,null);}
        public boolean install(String id){return mutate("install",inventory.get(id),null);}
        public boolean installNewFromCatalog(String id){
            if(!COIL_5V.equals(id)&&!COIL_12V.equals(id))throw new IllegalArgumentException("Unknown relay catalog choice");
            return mutate("catalog",null,id);
        }
        private boolean mutate(String operation,PhysicalRelayPart requested,final String catalog){
            if(!safe())throw new BoardModificationRejectedException("Disconnect all supplies and wait for coil discharge before replacing the relay");
            if("remove".equals(operation)?slot.isEmpty():!slot.isEmpty())return false;
            PhysicalMutationScope scope=new PhysicalMutationScope(sim,owner,modifications,
                PhysicalMutationIntent.prepare(owner.getPhysicalBoardRuntime(),owner,modifications,slot,operation,null,catalog,requested));
            try {
                if("remove".equals(operation)) {
                    modifications.disconnectComponentForMutation(scope,getComponentId());scope.clearPart();
                } else {
                    PhysicalRelayPart part=requested;
                    if(catalog!=null){
                        final RelaySpecification spec=new RelaySpecification(COIL_5V.equals(catalog)?5:12);
                        int left=0;for(CircuitElm e:owner.getSimulationElements())left=Math.min(left,Math.min(e.x,e.x2));
                        if(left < -1000000)throw new IllegalStateException("Relay backing allocation exhausted");
                        final ServiceRelayElm element=spec.create(left-1024,1024);
                        part=scope.acquire(inventory,getComponentId()+"_CATALOG_PART",new PhysicalPartIdentityFactory<PhysicalRelayPart>(){
                            public PhysicalRelayPart create(String id){
                                PhysicalRelayPart p=new PhysicalRelayPart(id,spec,element,null,new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED,id));
                                slot.physical.bindGeometryForAcquisition(p);return p;
                            }
                        });
                        scope.registerCanonicalElement(element);scope.appendActiveElement(element);
                    }
                    scope.replacePrimaryBinding(part.getElement());
                    for(GeneratedComponentConnectionBinding b:owner.getConnectionBindings().getForComponent(getComponentId()))
                        scope.retargetEndpoint(b,part.terminal(owner.getBoard().getPad(b.getPadId()).getTerminalId()));
                    scope.installPart(part);scope.restoreComponentGraph();
                }
                scope.commit();
            }catch(Throwable failure){scope.abort(failure);PhysicalMutationScope.rethrow(failure);return false;}
            scope.closeAfterCommit();
            try{
                sim.getGeneratedChallengeController().invalidateCustomerRetest();sim.needAnalyze();
                sim.requestGeneratedBoardVerification();sim.refreshBoardModificationControls();
            }catch(Throwable failure){sim.markGeneratedRuntimeFailure(owner,failure);PhysicalMutationScope.rethrow(failure);}
            return true;
        }
    }
    private static final class RelaySlot implements PhysicalMutationSlot {
        final PhysicalBoardSlot physical;
        final WireElm[] attachments;
        RelaySlot(PhysicalBoardSlot physical,PhysicalRelayPart original,WireElm[] attachments){
            if(attachments==null||attachments.length!=5)throw new IllegalArgumentException("Relay needs five attachments");
            this.physical=physical;this.attachments=new WireElm[5];
            for(int i=0;i<5;i++){ if(attachments[i]==null)throw new IllegalArgumentException("Missing relay attachment"); this.attachments[i]=attachments[i]; }
            physical.install(original);
        }
        public String getComponentId(){return physical.getComponentId();}
        public PhysicalBoardSlot getPhysicalSlot(){return physical;}
        public PhysicalRelayPart getInstalledPart(){return (PhysicalRelayPart)physical.getInstalledPart();}
        public boolean isEmpty(){return !physical.isOccupied();}
        public boolean acceptsPart(PhysicalPart<?> p){return p instanceof PhysicalRelayPart && p.getPackage().isEquivalentTo(PhysicalPackages.RELAY_SPDT);}
        public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> p,BoardPad pad){
            if(!acceptsPart(p)||pad==null||!getComponentId().equals(pad.getComponentId()))throw new IllegalArgumentException("Foreign relay pad");
            return ((PhysicalRelayPart)p).terminal(pad.getTerminalId());
        }
        public void installForMutation(PhysicalPart<?> p,PhysicalMutationScope scope){
            if(!scope.owns(this)||!acceptsPart(p))throw new IllegalArgumentException("Foreign relay scope");
            for(int i=0;i<5;i++){
                CircuitPostMeasurementEndpoint ep=(CircuitPostMeasurementEndpoint)p.getTerminal(i).getEndpoint();
                Point point=ep.getElement().getPost(ep.getPostIndex());
                attachments[i].x2=point.x;attachments[i].y2=point.y;attachments[i].setPoints();scope.afterAttachmentWrite();
            }
            physical.install(p);scope.afterSlotMountWrite();
        }
        public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope){
            if(!scope.owns(this))throw new IllegalArgumentException("Foreign relay scope");
            PhysicalPart<?> p=physical.remove();scope.afterSlotClearWrite();return p;
        }
        private final class State implements AttachmentState{
            final RelaySlot owner = RelaySlot.this;
            final int[] xy=new int[10];
            State(){for(int i=0;i<5;i++){xy[2*i]=attachments[i].x2;xy[2*i+1]=attachments[i].y2;}}
        }
        public AttachmentState captureAttachmentState(){return new State();}
        public void restoreAttachmentState(AttachmentState s){
            if(!(s instanceof State) || ((State)s).owner != this)throw new IllegalArgumentException("Foreign relay attachment snapshot");
            State state=(State)s;for(int i=0;i<5;i++){attachments[i].x2=state.xy[2*i];attachments[i].y2=state.xy[2*i+1];attachments[i].setPoints();}
        }
    }
}
