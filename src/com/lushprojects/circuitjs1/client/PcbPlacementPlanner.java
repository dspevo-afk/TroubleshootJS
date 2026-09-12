package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.TreeMap;
import java.util.Vector;

/** Bounded demand sizing, domain/region packing, connector anchoring and local topology refinement. */
final class PcbPlacementPlanner {
    static final int OUTLINE_CANDIDATES=6, MAX_PARTS=128, MAX_EXTENT=16000, CHANNEL=40;
    static final long MAX_AREA=64000000L;
    static final class Rejected extends RuntimeException {
        final String reason;
        Rejected(String reason) { super(reason); this.reason=reason; }
    }
    static final class Plan {
        final Rectangle outline;
        final Vector<PcbFootprint> footprints;
        final Vector<PcbLayoutRegion> regions;
        final long courtyardArea, demandArea;
        final int candidate, evaluations;
        Plan(Rectangle outline, Vector<PcbFootprint> footprints, Vector<PcbLayoutRegion> regions,
                long courtyardArea,long demandArea,int candidate,int evaluations) {
            this.outline=new Rectangle(outline); this.footprints=new Vector<PcbFootprint>(footprints);
            this.regions=new Vector<PcbLayoutRegion>(regions); this.courtyardArea=courtyardArea;
            this.demandArea=demandArea; this.candidate=candidate; this.evaluations=evaluations;
        }
        PcbBoardLayout materialize() {
            PcbBoardLayout layout=new PcbBoardLayout(outline.x+outline.width+250,outline.y+outline.height+60,
                outline,new Rectangle(outline.x+outline.width+40,40,180,300));
            for (PcbFootprint footprint : footprints) {
                layout.addComponent(footprint.getPlacement()); for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
            }
            for (PcbLayoutRegion region : regions) layout.addRegion(region);
            return layout;
        }
    }
    private static final class Item {
        PcbFootprint footprint;
        final Rectangle envelope;
        final PcbPlacementConstraints.Part demand;
        Item(PcbFootprint f,PcbPlacementConstraints.Part d) { footprint=f; demand=d; envelope=envelope(f,d.accessMargin); }
    }
    private static final class Region {
        final String key,domain;
        final Vector<Item> items=new Vector<Item>();
        int width,height,margin;
        Region(String key,String domain,int margin) { this.key=key; this.domain=domain; this.margin=margin; }
    }
    private final PcbFootprintRegistry registry;
    PcbPlacementPlanner(PcbFootprintRegistry registry) {
        if (registry==null) throw new IllegalArgumentException("Missing footprint registry"); this.registry=registry;
    }
    Plan plan(TroubleshootBoard board,PcbPlacementConstraints constraints,long seed,int candidate) {
        if (board==null || constraints==null) throw new IllegalArgumentException("Missing placement inputs");
        constraints.validate(board);
        if (board.getComponentIds().isEmpty() || board.getComponentIds().size()>MAX_PARTS) throw new Rejected("PART_LIMIT");
        int variant=(candidate % OUTLINE_CANDIDATES + OUTLINE_CANDIDATES) % OUTLINE_CANDIDATES;
        Random random=new Random(seed ^ (0x632be59bd9b4e019L*(candidate+1)));
        TreeMap<String,Region> groups=new TreeMap<String,Region>(); Vector<Item> anchors=new Vector<Item>();
        long demandArea=0,courtyardArea=0; int maxWidth=0,anchorWidth=0;
        for (PcbPlacementConstraints.Part declaration : constraints.getParts()) {
            BoardComponent component=board.getComponent(declaration.componentId);
            // Edge-oriented packages choose their declared inward variant before translation.
            Rectangle reference=new Rectangle(0,0,4000,4000);
            int origin=declaration.anchor==PcbPlacementConstraints.Anchor.RIGHT ? 3600 : 0;
            PcbFootprint source=registry.create(component,origin,0,random,reference).translated(0,0);
            Item item=new Item(source,declaration); Rectangle court=source.getPlacement().getRoutingCourtyard();
            courtyardArea+=(long)court.width*court.height;
            demandArea+=(long)(item.envelope.width+CHANNEL)*(item.envelope.height+CHANNEL);
            maxWidth=Math.max(maxWidth,item.envelope.width+CHANNEL);
            if (declaration.anchor!=PcbPlacementConstraints.Anchor.NONE) {
                for (PcbPadPlacement pad : source.getPads()) if (pad.getEscapeLength()>0 &&
                        (declaration.anchor==PcbPlacementConstraints.Anchor.RIGHT && pad.getEscapeDx()>0 ||
                         declaration.anchor==PcbPlacementConstraints.Anchor.LEFT && pad.getEscapeDx()<0)) throw new Rejected("CONNECTOR_FACING_OUTWARD");
                anchors.add(item); anchorWidth=Math.max(anchorWidth,item.envelope.width+CHANNEL);
            } else {
                String key=declaration.domainId+"/"+declaration.regionId;
                Region region=groups.get(key);
                if (region==null) { region=new Region(key,declaration.domainId,constraints.margin(declaration.domainId)); groups.put(key,region); }
                region.items.add(item);
            }
        }
        if (demandArea>MAX_AREA/2) throw new Rejected("DEMAND_LIMIT");
        anchorWidth=align(anchorWidth); maxWidth=align(maxWidth);
        boolean leftAnchored=false,rightAnchored=false;
        for(Item item:anchors) { leftAnchored |= item.demand.anchor==PcbPlacementConstraints.Anchor.LEFT; rightAnchored |= item.demand.anchor==PcbPlacementConstraints.Anchor.RIGHT; }
        double[] aspects={1.4,1.9,1.0,2.4,1.2,1.65};
        int contentWidth=align((int)Math.ceil(Math.sqrt(demandArea*aspects[variant])));
        contentWidth=Math.max(maxWidth+CHANNEL,contentWidth);
        int width=align(contentWidth+(leftAnchored?anchorWidth:0)+(rightAnchored?anchorWidth:0)+80);
        if (width>MAX_EXTENT) throw new Rejected("WIDTH_LIMIT");
        TopologyPlacementGraph topology=new TopologyPlacementGraph(board);
        Vector<Region> regions=new Vector<Region>(groups.values());
        // Alternate regional order is bounded global feedback; no fixed region walls block nets.
        if (variant%2==1) Collections.reverse(regions);
        int left=40+(leftAnchored?anchorWidth:0), x=left,y=50,rowHeight=0;
        Vector<PcbFootprint> placed=new Vector<PcbFootprint>(); int evaluations=0;
        for (Region region : regions) {
            long regionArea=0; int widest=0;
            for (Item item : region.items) { regionArea+=(long)(item.envelope.width+CHANNEL)*(item.envelope.height+CHANNEL); widest=Math.max(widest,item.envelope.width); }
            int roomWidth=Math.min(contentWidth, Math.max(widest+2*region.margin,align((int)Math.sqrt(regionArea*aspects[variant])+2*region.margin)));
            order(region.items,topology,seed ^ (0x9e3779b97f4a7c15L*(candidate+1)));
            int rx=region.margin,ry=region.margin,rh=0,used=0;
            for (Item item : region.items) {
                if (rx+item.envelope.width+region.margin>roomWidth && rx>region.margin) { rx=region.margin; ry+=rh+CHANNEL; rh=0; }
                item.footprint=item.footprint.translated(align(rx-item.envelope.x),align(ry-item.envelope.y));
                rx+=item.envelope.width+CHANNEL; used=Math.max(used,rx-CHANNEL+region.margin); rh=Math.max(rh,item.envelope.height);
                evaluations++;
            }
            region.width=align(used); region.height=align(ry+rh+region.margin);
            if (x+region.width>left+contentWidth && x>left) { x=left; y+=rowHeight+CHANNEL; rowHeight=0; }
            for (Item item : region.items) {
                item.footprint=item.footprint.translated(item.footprint.getPlacement().getX()+x,item.footprint.getPlacement().getY()+y);
                placed.add(item.footprint);
            }
            x+=region.width+CHANNEL; rowHeight=Math.max(rowHeight,region.height);
        }
        int anchorY=60;
        for (Item item : anchors) {
            int ax=item.demand.anchor==PcbPlacementConstraints.Anchor.RIGHT ? width-40-item.envelope.width : 40;
            item.footprint=item.footprint.translated(align(ax-item.envelope.x),align(anchorY-item.envelope.y));
            placed.add(item.footprint); anchorY+=item.envelope.height+CHANNEL+constraints.margin(item.demand.domainId)*2;
        }
        int height=align(Math.max(y+rowHeight+50,anchorY+30));
        if (height>MAX_EXTENT || (long)width*height>MAX_AREA || (long)width*height>demandArea*6+300000)
            throw new Rejected("OUTLINE_BUDGET");
        Rectangle outline=new Rectangle(20,20,width,height);
        // Local topology feedback only accepts translations whose full physical envelopes remain free.
        for (int pass=0;pass<2;pass++) for (int i=0;i<placed.size();i++) {
            PcbFootprint current=placed.get(i); PcbPlacementConstraints.Part declaration=constraints.get(current.getPlacement().getComponentId());
            if (declaration.anchor!=PcbPlacementConstraints.Anchor.NONE) continue;
            double best=wireDemand(current,placed,topology);
            Point target=weightedConnectedTarget(current.translated(0,0),placed,
                topology.getLinksFor(declaration.componentId),current.getPlacement().getX(),current.getPlacement().getY());
            int dx=target.x<current.getPlacement().getX()?-10:10;
            int dy=target.y<current.getPlacement().getY()?-10:10;
            for (int[] delta : new int[][]{{dx,0},{0,dy},{-dx,0},{0,-dy}}) {
                PcbFootprint trial=current.translated(current.getPlacement().getX()+delta[0],current.getPlacement().getY()+delta[1]);
                evaluations++;
                if (!fits(trial,outline,placed,i,constraints)) continue;
                double score=wireDemand(trial,placed,topology);
                if (score<best) { current=trial; best=score; placed.set(i,current); }
            }
        }
        validate(board,constraints,outline,placed);
        Vector<PcbLayoutRegion> navigation=new Vector<PcbLayoutRegion>();
        TreeMap<String,Vector<String>> members=new TreeMap<String,Vector<String>>(); TreeMap<String,String> labels=new TreeMap<String,String>();
        for (PcbPlacementConstraints.Part part : constraints.getParts()) {
            if (!members.containsKey(part.regionId)) members.put(part.regionId,new Vector<String>());
            members.get(part.regionId).add(part.componentId); labels.put(part.regionId,part.regionLabel);
        }
        for (String id : members.keySet()) navigation.add(new PcbLayoutRegion(id,labels.get(id),members.get(id)));
        return new Plan(outline,placed,navigation,courtyardArea,demandArea,variant,evaluations);
    }
    static Rectangle envelope(PcbFootprint footprint,int margin) {
        Rectangle r=footprint.getPlacement().getSelectionEnvelope();
        Rectangle c=footprint.getPlacement().getRoutingCourtyard(); r=union(r,c);
        for (PcbPadPlacement pad : footprint.getPads()) {
            int ex=pad.getX()+pad.getEscapeDx()*pad.getEscapeLength(),ey=pad.getY()+pad.getEscapeDy()*pad.getEscapeLength();
            r=union(r,new Rectangle(ex-10,ey-10,20,20));
        }
        return new Rectangle(r.x-margin,r.y-margin-20,r.width+margin*2,r.height+margin*2+20);
    }
    private static Rectangle union(Rectangle a,Rectangle b) {
        int x=Math.min(a.x,b.x),y=Math.min(a.y,b.y);
        return new Rectangle(x,y,Math.max(a.x+a.width,b.x+b.width)-x,Math.max(a.y+a.height,b.y+b.height)-y);
    }
    private static void order(Vector<Item> items,final TopologyPlacementGraph topology,final long seed) {
        Collections.sort(items,new Comparator<Item>() { public int compare(Item a,Item b) {
            double da=degree(a,topology),db=degree(b,topology);
            if (da!=db) return da>db?-1:1;
            String ai=a.demand.componentId,bi=b.demand.componentId;
            int ah=ai.hashCode()^(int)seed,bh=bi.hashCode()^(int)seed;
            return ah==bh ? ai.compareTo(bi) : ah<bh?-1:1;
        }});
        Vector<Item> remaining=new Vector<Item>(items),ordered=new Vector<Item>();
        while(!remaining.isEmpty()) {
            Item best=remaining.get(0);double score=-1;
            for(Item candidate:remaining) {
                double connected=0;
                for(TopologyPlacementGraph.PadLink link:topology.getLinksFor(candidate.demand.componentId))
                    for(Item previous:ordered) if(previous.demand.componentId.equals(link.getOtherComponentId())) connected+=link.getWeight();
                if(connected>score){best=candidate;score=connected;}
            }
            ordered.add(best);remaining.remove(best);
        }
        items.clear();items.addAll(ordered);
    }
    private static double degree(Item item,TopologyPlacementGraph topology) {
        double n=0; for(TopologyPlacementGraph.PadLink link:topology.getLinksFor(item.demand.componentId)) n+=link.getWeight(); return n;
    }
    private static double wireDemand(PcbFootprint f,Vector<PcbFootprint> placed,TopologyPlacementGraph topology) {
        double score=0;
        for(TopologyPlacementGraph.PadLink link:topology.getLinksFor(f.getPlacement().getComponentId()))
            for(PcbFootprint other:placed) if(other.getPlacement().getComponentId().equals(link.getOtherComponentId())) {
                PcbPadPlacement a=f.getPad(link.getPadId()),b=other.getPad(link.getOtherPadId());
                score+=(Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY()))*link.getWeight();
            }
        return score;
    }
    private static boolean fits(PcbFootprint f,Rectangle outline,Vector<PcbFootprint> placed,int self,PcbPlacementConstraints constraints) {
        PcbPlacementConstraints.Part demand=constraints.get(f.getPlacement().getComponentId()); Rectangle r=envelope(f,demand.accessMargin);
        if(r.x<outline.x+10 || r.y<outline.y+10 || r.x+r.width>outline.x+outline.width-10 || r.y+r.height>outline.y+outline.height-10) return false;
        for(int i=0;i<placed.size();i++) if(i!=self) {
            PcbFootprint other=placed.get(i); PcbPlacementConstraints.Part od=constraints.get(other.getPlacement().getComponentId());
            Rectangle b=envelope(other,od.accessMargin); if(r.intersects(b)) return false;
            for(PcbPlacementConstraints.Barrier barrier:constraints.getBarriers()) if(barrier.separates(demand.domainId,od.domainId)) {
                Rectangle ac=f.getPlacement().getRoutingCourtyard(),bc=other.getPlacement().getRoutingCourtyard();
                int dx=Math.max(ac.x-bc.x-bc.width,bc.x-ac.x-ac.width),dy=Math.max(ac.y-bc.y-bc.height,bc.y-ac.y-ac.height);
                if(Math.max(dx,dy)<barrier.clearance) return false;
            }
        }
        return true;
    }
    static void validate(TroubleshootBoard board,PcbPlacementConstraints constraints,Rectangle outline,Vector<PcbFootprint> placed) {
        if(placed.size()!=board.getComponentIds().size()) throw new Rejected("MISSING_COMPONENT");
        java.util.HashSet<String> seen=new java.util.HashSet<String>();
        for(int i=0;i<placed.size();i++) {
            PcbFootprint f=placed.get(i);
            if(!seen.add(f.getPlacement().getComponentId())) throw new Rejected("DUPLICATE_COMPONENT");
            if(!fits(f,outline,placed,i,constraints)) throw new Rejected("ENVELOPE_ACCESS_OR_DOMAIN");
            for(PcbPadPlacement pad:f.getPads()) {
                int ex=pad.getX()+pad.getEscapeDx()*pad.getEscapeLength(),ey=pad.getY()+pad.getEscapeDy()*pad.getEscapeLength();
                for(PcbFootprint other:placed) if(other!=f && other.getPlacement().getRoutingCourtyard().contains(ex,ey))
                    throw new Rejected("STRANDED_ESCAPE");
            }
        }
        PcbAccessPlanner.validate(outline,placed);
    }
    /**
     * Calculates an unplaced component origin from already placed neighbors.
     *
     * <p>The prototype's pad coordinates are package-local (the prototype is
     * created at 0,0), while each neighbor pad is already in board/world
     * coordinates.  Therefore each contribution is {@code worldPad -
     * prototypeLocalPad}; the board-center fallback is used only when no
     * positive-weight neighbor is available.  This helper is pure so that the
     * coordinate contract can be tested without consuming placement randomness
     * or running the candidate-offset search.</p>
     */
    static Point weightedConnectedTarget(PcbFootprint prototype,
            Vector<PcbFootprint> placed, Vector<TopologyPlacementGraph.PadLink> links,
            int fallbackX, int fallbackY) {
        if (prototype == null || placed == null || links == null)
            throw new IllegalArgumentException("Missing PCB placement target inputs");
        double weightedX = 0;
        double weightedY = 0;
        double totalWeight = 0;
        boolean connected = false;
        for (TopologyPlacementGraph.PadLink link : links) {
            if (link == null)
                throw new IllegalArgumentException("Missing PCB topology placement link");
            double weight = link.getWeight();
            if (Double.isNaN(weight) || Double.isInfinite(weight) || weight < 0)
                throw new IllegalArgumentException("Invalid PCB topology placement weight: " +
                    weight);
            if (weight == 0)
                continue;
            PcbFootprint other = findPlacedFootprint(placed, link.getOtherComponentId());
            if (other == null)
                continue;
            PcbPadPlacement sourcePad = prototype.getPad(link.getPadId());
            PcbPadPlacement otherPad = other.getPad(link.getOtherPadId());
            long deltaX = (long) otherPad.getX() - sourcePad.getX();
            long deltaY = (long) otherPad.getY() - sourcePad.getY();
            double contributionX = deltaX * weight;
            double contributionY = deltaY * weight;
            if (Double.isNaN(contributionX) || Double.isInfinite(contributionX) ||
                    Double.isNaN(contributionY) || Double.isInfinite(contributionY))
                throw new IllegalArgumentException("PCB placement target arithmetic overflow");
            weightedX += contributionX;
            weightedY += contributionY;
            totalWeight += weight;
            if (Double.isNaN(weightedX) || Double.isInfinite(weightedX) ||
                    Double.isNaN(weightedY) || Double.isInfinite(weightedY) ||
                    Double.isNaN(totalWeight) || Double.isInfinite(totalWeight))
                throw new IllegalArgumentException("PCB placement target arithmetic overflow");
            connected = true;
        }
        if (!connected)
            return new Point(fallbackX, fallbackY);
        return new Point(checkedRoundedTarget(weightedX / totalWeight),
            checkedRoundedTarget(weightedY / totalWeight));
    }

    private static int checkedRoundedTarget(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException("PCB placement target is not finite");
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE)
            throw new IllegalArgumentException("PCB placement target is out of range: " + value);
        return (int) rounded;
    }

    private static PcbFootprint findPlacedFootprint(Vector<PcbFootprint> footprints,
            String componentId) {
        for (PcbFootprint footprint : footprints) {
            if (footprint == null)
                throw new IllegalArgumentException("Missing placed PCB footprint");
            if (footprint.getPlacement().getComponentId().equals(componentId))
                return footprint;
        }
        return null;
    }

    private static int align(int value) { return (value+9)/10*10; }
}
