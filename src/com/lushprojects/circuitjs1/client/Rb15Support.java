package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;

/** Causal entry, conditioning and indication circuitry, separate from output construction. */
final class Rb15Support {
    private final RelayOutputGenerator.Assembly a;
    private final Vector<PhysicalPart<?>> parts=new Vector<PhysicalPart<?>>();
    final LEDElm led;
    Rb15Support(RelayOutputGenerator.Assembly assembly, Rb15Plan plan) {
        a=assembly;
        ProtectionFuseElm fuse=new ProtectionFuseElm(8000,400); fuse.drag(8080,400);
        // 250 mA continuous threshold with 0.1875 A²s thermal capacity; existing I²t model.
        fuse.i2t=.1875;
        BasicPhysicalSpecification fuseSpec=new BasicPhysicalSpecification("F1_250mA");
        PhysicalNameplate fuseLabel=new PhysicalNameplate("F1","250 mA fuse","Markings","250 mA / 24 V DC");
        add("F1","FUSE",PhysicalPackages.AXIAL_FUSE,fuseSpec,fuseLabel,fuse,"RAW_INPUT","FUSED_INPUT");
        parts.add(PhysicalFoundationPartFactory.fromBoardBindings("F1",fuseSpec,fuseLabel,
            PhysicalPackages.AXIAL_FUSE,a.board.getSimulationBindings(),fuse,provenance("F1")));
        diode("DREV","FUSED_INPUT","CTRL_SUPPLY");
        capacitor("C1",1e-6,"1 uF / 25 V","CTRL_SUPPLY","CTRL_RETURN");
        resistor("RBLEED",2200,"CTRL_SUPPLY","CTRL_RETURN");
        resistor("RIN",1000,"CMD","FILTERED_CMD");
        // The existing driver pull-down discharges this capacitor even when
        // RDRIVE is open and both external supplies are disconnected.
        if(plan.filtered) capacitor("C2",1e-8,"10 nF / 25 V","DRIVE","CTRL_RETURN");
        else resistor("RBIAS",100000,"FILTERED_CMD","CTRL_RETURN");
        resistor("RLED",3300,"CTRL_SUPPLY","LED_FEED");
        led=new LEDElm(10000,400); led.drag(10080,400); led.modelName="default-led"; led.setup();
        LedNameplate ledSpec=new LedNameplate("LED1","Red power indicator","default-led",1,0,0);
        PhysicalNameplate label=new PhysicalNameplate("LED1","Red power indicator","Part","Red LED");
        add("LED1","LED",PhysicalPackages.THROUGH_HOLE_LED,ledSpec,label,led,"LED_FEED","CTRL_RETURN");
        parts.add(new PhysicalLedPart("LED1",ledSpec,ledSpec,led,false,LedPartLocation.INSTALLED,provenance("LED1")));
    }
    void install(PhysicalBoardRuntime runtime) {
        for(PhysicalPart<?> part:parts) runtime.createSlot(part.getId()).install(part);
    }
    private void resistor(String id,double value,String first,String second) {
        ResistorElm element=new ResistorElm(8200+parts.size()*256,400); element.drag(element.x+80,400); element.setResistance(value);
        ResistorNameplate spec=new ResistorNameplate(id,value,5,.25);
        add(id,"RESISTOR",PhysicalPackages.AXIAL_RESISTOR,spec,new PhysicalNameplate(id,"Resistor markings","Markings","Color bands"),element,first,second);
        parts.add(new PhysicalResistorPart(id,spec,spec,element,null,null,ResistorPartLocation.INSTALLED,provenance(id)));
    }
    private void capacitor(String id,double value,String marking,String first,String second) {
        CapacitorElm element=new CapacitorElm(8200+parts.size()*256,400); element.drag(element.x+80,400); element.setCapacitance(value);
        CapacitorNameplate markings=new CapacitorNameplate("Ceramic capacitor",marking);
        CapacitorSpecification spec=new CapacitorSpecification(id,value,10,25,PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,markings);
        add(id,"CAPACITOR",PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,spec,markings.forPhysicalPartId(id),element,first,second);
        parts.add(new PhysicalCapacitorPart(id,spec,markings.forPhysicalPartId(id),element,null,CapacitorPartLocation.INSTALLED,provenance(id)));
    }
    private void diode(String id,String first,String second) {
        DiodeElm element=new DiodeElm(8200+parts.size()*256,400); element.drag(element.x+80,400); element.modelName="1N4148"; element.setup();
        DiodeNameplate spec=new DiodeNameplate(id,"1N4148 reverse-polarity protection","1N4148");
        add(id,"DIODE",PhysicalPackages.AXIAL_DIODE,spec,new PhysicalNameplate(id,"1N4148 diode","Markings","1N4148"),element,first,second);
        parts.add(new PhysicalDiodePart(id,spec,spec,element,null,false,DiodePartLocation.INSTALLED,provenance(id)));
    }
    private void add(String id,String type,PhysicalPackage physical,PhysicalSpecification spec,
            PhysicalNameplate label,CircuitElm element,String first,String second) {
        a.specifications.addPhysicalDefinition(id,spec,label,physical);
        Vector<String> terminals=physical.getTerminalIds();
        a.part(id,type,physical,new String[]{terminals.get(0),terminals.get(1)},new String[]{first,second},new int[]{0,1},element,false);
    }
    private PhysicalPartProvenance provenance(String id) { return new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED,id); }
    static PowerDomainContract powerContract() {
        Vector<PowerDomainContract.Rail> rails=new Vector<PowerDomainContract.Rail>();
        for(String net:new String[]{"RAW_INPUT","FUSED_INPUT","CTRL_SUPPLY","CMD","FILTERED_CMD","DRIVE","COIL_LOW","CONTACT_OUT","NC","LED_FEED"})
            rails.add(new PowerDomainContract.Rail(net,"CTRL_RETURN",PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED));
        return new PowerDomainContract("RB15_LOW_VOLTAGE",Arrays.asList(new PowerDomainContract.Reference("CTRL_RETURN","CONTROL",null,false)),rails,
            Arrays.asList(source("COIL_INPUT","RAW_INPUT",12),source("COMMAND_INPUT","CMD",5)),Collections.<PowerDomainContract.BackfeedPath>emptyList());
    }
    private static PowerDomainContract.Source source(String id,String net,double volts) {
        return new PowerDomainContract.Source(id,net,Range.known(0,volts),Scalar.known(.5),Scalar.known(.05),Scalar.known(.25),Drive.RESISTIVE_SOURCE);
    }
}
