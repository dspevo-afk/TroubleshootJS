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
    /*
     * The original regional row packer is deliberately retained for the
     * small-board envelope.  Medium boards need a different placement shape:
     * a region is useful for navigation, but it is not a physical wall.  Keep
     * this threshold a generic demand rule instead of a family or component
     * exception so new 20-40 part providers use the same planner.
     */
    private static final int MEDIUM_MIN_PARTS=20;
    private static final int MEDIUM_BORDER=80;
    private static final int MEDIUM_STEP=40;
    private static final int MEDIUM_CHAIN_GAP=120;
    private static final int MEDIUM_MAX_EVALUATIONS=60000;
    private static final long MEDIUM_MIN_AREA=5000000L;
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
    private static final class RegionHint {
        final String key;
        double xFraction,yFraction;
        RegionHint(String key) { this.key=key; }
    }
    private final PcbFootprintRegistry registry;
    PcbPlacementPlanner(PcbFootprintRegistry registry) {
        if (registry==null) throw new IllegalArgumentException("Missing footprint registry"); this.registry=registry;
    }
    Plan plan(TroubleshootBoard board,PcbPlacementConstraints constraints,long seed,int candidate) {
        if (board==null || constraints==null) throw new IllegalArgumentException("Missing placement inputs");
        constraints.validate(board);
        if (board.getComponentIds().isEmpty() || board.getComponentIds().size()>MAX_PARTS) throw new Rejected("PART_LIMIT");
        int offset = constraints.routingLayer == PcbCopperLayer.BOTTOM ?
            (int)((seed ^ (seed >>> 32)) & 0x7fffffffL) % OUTLINE_CANDIDATES : 0;
        // Give the root-selected small-board shape several ordering attempts
        // before changing aspect. Otherwise the easiest shape wins every seed.
        int shapeAttempt=constraints.routingLayer==PcbCopperLayer.BOTTOM &&
            board.getComponentIds().size()<=5 ? candidate/OUTLINE_CANDIDATES : candidate;
        int variant=(shapeAttempt + offset) % OUTLINE_CANDIDATES;
        Random random=new Random(seed ^ (0x632be59bd9b4e019L*(candidate+1)));
        TreeMap<String,Region> groups=new TreeMap<String,Region>(); Vector<Item> anchors=new Vector<Item>();
        long demandArea=0,courtyardArea=0; int maxWidth=0,anchorWidth=0;
        for (PcbPlacementConstraints.Part declaration : constraints.getParts()) {
            BoardComponent component=board.getComponent(declaration.componentId);
            // Edge-oriented packages choose their declared inward variant before translation.
            Rectangle reference=new Rectangle(0,0,4000,4000);
            PcbPlacementConstraints.Anchor resolvedAnchor=resolvedAnchor(declaration,seed,candidate);
            int origin=resolvedAnchor==PcbPlacementConstraints.Anchor.RIGHT ? 3600 : 0;
            PcbFootprint source=registry.create(component,origin,0,random,reference).translated(0,0);
            Item item=new Item(source,declaration); Rectangle court=source.getPlacement().getRoutingCourtyard();
            courtyardArea+=(long)court.width*court.height;
            demandArea+=(long)(item.envelope.width+CHANNEL)*(item.envelope.height+CHANNEL);
            maxWidth=Math.max(maxWidth,item.envelope.width+CHANNEL);
            if (resolvedAnchor!=PcbPlacementConstraints.Anchor.NONE) {
                for (PcbPadPlacement pad : source.getPads()) if (pad.getEscapeLength()>0 &&
                        (resolvedAnchor==PcbPlacementConstraints.Anchor.RIGHT && pad.getEscapeDx()>0 ||
                         resolvedAnchor==PcbPlacementConstraints.Anchor.LEFT && pad.getEscapeDx()<0)) throw new Rejected("CONNECTOR_FACING_OUTWARD");
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
        if (board.getComponentIds().size()>=MEDIUM_MIN_PARTS)
            return planMedium(board,constraints,seed,candidate,groups,anchors,
                demandArea,courtyardArea,maxWidth,anchorWidth);
        boolean leftAnchored=false,rightAnchored=false;
        for(Item item:anchors) {
            PcbPlacementConstraints.Anchor side=resolvedAnchor(item.demand,seed,candidate);
            leftAnchored |= side==PcbPlacementConstraints.Anchor.LEFT;
            rightAnchored |= side==PcbPlacementConstraints.Anchor.RIGHT;
        }
        // Tiny circuits need both single-column and single-row candidates.
        double[] aspects=constraints.routingLayer==PcbCopperLayer.BOTTOM &&
                board.getComponentIds().size()<=5 ?
            new double[]{1.0,2.4,3.6,5.0,3.0,4.2} : new double[]{1.4,1.9,1.0,2.4,1.2,1.65};
        int contentWidth=align((int)Math.ceil(Math.sqrt(demandArea*aspects[variant])));
        contentWidth=Math.max(maxWidth+CHANNEL,contentWidth);
        int width=align(contentWidth+(leftAnchored?anchorWidth:0)+(rightAnchored?anchorWidth:0)+80);
        if (width>MAX_EXTENT) throw new Rejected("WIDTH_LIMIT");
        TopologyPlacementGraph topology=new TopologyPlacementGraph(board);
        Vector<Region> regions=new Vector<Region>(groups.values());
        // Alternate regional order is bounded global feedback; no fixed region walls block nets.
        if (variant%2==1) Collections.reverse(regions);
        // Seed whole functional regions from the first attempt, not only after failed canonical layouts.
        if(constraints.routingLayer==PcbCopperLayer.BOTTOM) NamedRandomStreams.shuffle(regions,random);
        int left=40+(leftAnchored?anchorWidth:0), x=left,y=50,rowHeight=0;
        Vector<PcbFootprint> placed=new Vector<PcbFootprint>(); int evaluations=0;
        for (Region region : regions) {
            long regionArea=0; int widest=0;
            for (Item item : region.items) { regionArea+=(long)(item.envelope.width+CHANNEL)*(item.envelope.height+CHANNEL); widest=Math.max(widest,item.envelope.width); }
            int roomWidth=Math.min(contentWidth, Math.max(widest+2*region.margin,align((int)Math.sqrt(regionArea*aspects[variant])+2*region.margin)));
            order(region.items,topology,seed ^ (0x9e3779b97f4a7c15L*(candidate+1)));
            if (constraints.routingLayer == PcbCopperLayer.BOTTOM && region.items.size() > 1) {
                // Keep topology-neighbor ordering, but vary its start and direction.
                // Different roots must not all converge on the same ranked first part.
                int rotateBy=random.nextInt(region.items.size());
                // GWT 2.7 does not emulate Collections.rotate.
                for(int turn=0;turn<rotateBy;turn++)
                    region.items.add(0,region.items.remove(region.items.size()-1));
                if (random.nextBoolean()) Collections.reverse(region.items);
                if (candidate >= OUTLINE_CANDIDATES) NamedRandomStreams.shuffle(region.items,random);
            }
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
        int leftStack=0,rightStack=0;
        for (Item item:anchors) {
            int span=item.envelope.height+CHANNEL+constraints.margin(item.demand.domainId)*2;
            if (resolvedAnchor(item.demand,seed,candidate)==PcbPlacementConstraints.Anchor.RIGHT) rightStack+=span;
            else leftStack+=span;
        }
        // An edge anchor is not a fixed corner. Use the actual available edge
        // span for seeded top/middle/bottom placement, not decorative jitter.
        int leftFree=Math.max(0,y+rowHeight-50-leftStack);
        int rightFree=Math.max(0,y+rowHeight-50-rightStack);
        int leftAnchorY=50+anchorOffset(seed,candidate,false);
        int rightAnchorY=50+anchorOffset(seed,candidate,true);
        if (constraints.routingLayer==PcbCopperLayer.BOTTOM) {
            leftAnchorY+=align(leftFree*random.nextInt(5)/4);
            rightAnchorY+=align(rightFree*random.nextInt(5)/4);
        }
        for (Item item : anchors) {
            PcbPlacementConstraints.Anchor side=resolvedAnchor(item.demand,seed,candidate);
            int anchorY=side==PcbPlacementConstraints.Anchor.RIGHT ? rightAnchorY : leftAnchorY;
            int ax=side==PcbPlacementConstraints.Anchor.RIGHT ? width-40-item.envelope.width : 40;
            item.footprint=item.footprint.translated(align(ax-item.envelope.x),align(anchorY-item.envelope.y));
            placed.add(item.footprint);
            int next=anchorY+item.envelope.height+CHANNEL+constraints.margin(item.demand.domainId)*2;
            if(side==PcbPlacementConstraints.Anchor.RIGHT) rightAnchorY=next; else leftAnchorY=next;
        }
        int anchorBottom=Math.max(leftAnchorY,rightAnchorY);
        int height=align(Math.max(y+rowHeight+50,anchorBottom+30));
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

    /**
     * Place a medium inventory from its electrical graph, while retaining the
     * provider supplied region and connector semantics.  Regions are hints for
     * navigation and lane selection; they are intentionally not packed as
     * isolated rectangles because a shared rail or return must be able to pass
     * between them.
     */
    private Plan planMedium(TroubleshootBoard board,
            PcbPlacementConstraints constraints,long seed,int candidate,
            TreeMap<String,Region> groups,Vector<Item> anchors,long demandArea,
            long courtyardArea,int maxWidth,int anchorWidth) {
        TopologyPlacementGraph topology=new TopologyPlacementGraph(board);
        Vector<Item> all=new Vector<Item>();
        TreeMap<String,Item> byId=new TreeMap<String,Item>();
        int maxHeight=0;
        for (Region region:groups.values()) for (Item item:region.items) {
            all.add(item); byId.put(item.demand.componentId,item);
            maxHeight=Math.max(maxHeight,item.envelope.height+CHANNEL);
        }
        for (Item item:anchors) {
            all.add(item); byId.put(item.demand.componentId,item);
            maxHeight=Math.max(maxHeight,item.envelope.height+CHANNEL);
        }
        final TreeMap<String,RegionHint> hints=mediumRegionHints(board,groups,anchors,
            byId,topology,seed,candidate);

        /*
         * Medium layouts reserve measured routing room in addition to the
         * package demand.  The area is still bounded by the global board and
         * by a demand-derived allowance, so a pathological package cannot turn
         * placement into unbounded outline growth.
         */
        double[] aspects=new double[]{1.55,1.90,2.20,1.35,2.50,1.75};
        int aspectIndex=(int)(((long)candidate+
            ((seed^(seed>>>32))&0x7fffffffL))%aspects.length);
        double aspect=aspects[aspectIndex];
        int partCount=board.getComponentIds().size();
        double spacingFactor=3.0;
        if(partCount>40)
            spacingFactor+=Math.min(6.0,(partCount-40)*.10);
        long targetArea=Math.max(MEDIUM_MIN_AREA,
            (long)Math.ceil(demandArea*spacingFactor)+500000L);
        /* Connector strips add a full-height allowance to the content width;
         * leave bounded headroom for that allowance on very large inventories. */
        if(partCount>40)targetArea=Math.min(targetArea,MAX_AREA-8000000L);
        targetArea=Math.min(targetArea,MAX_AREA-1000000L);
        int contentWidth=align((int)Math.ceil(Math.sqrt(targetArea*aspect)));
        contentWidth=Math.max(contentWidth,maxWidth+CHANNEL*4);
        int width=align(contentWidth+((anchorsOnSide(anchors,seed,candidate,
            PcbPlacementConstraints.Anchor.LEFT))?anchorWidth:0)+
            ((anchorsOnSide(anchors,seed,candidate,
            PcbPlacementConstraints.Anchor.RIGHT))?anchorWidth:0)+160);
        int height=align((int)Math.ceil(targetArea/(double)contentWidth));
        height=Math.max(height,MEDIUM_BORDER*2+maxHeight+CHANNEL*2);
        if (width>MAX_EXTENT || height>MAX_EXTENT)
            throw new Rejected("MEDIUM_OUTLINE_LIMIT");
        long outlineArea=(long)width*height;
        long mediumAreaLimit=partCount>40 ? MAX_AREA :
            Math.min(MAX_AREA,targetArea+2500000L);
        if (outlineArea>mediumAreaLimit)
            throw new Rejected("MEDIUM_OUTLINE_BUDGET");
        Rectangle outline=new Rectangle(20,20,width,height);

        Vector<PcbFootprint> placed=new Vector<PcbFootprint>();
        TreeMap<String,Item> remaining=new TreeMap<String,Item>();
        for (Item item:all) if (item.demand.anchor==PcbPlacementConstraints.Anchor.NONE)
            remaining.put(item.demand.componentId,item);
        int[] evaluations=new int[]{0};
        Vector<Item> orderedAnchors=new Vector<Item>(anchors);
        Collections.sort(orderedAnchors,new Comparator<Item>() { public int compare(Item a,Item b) {
            RegionHint ah=hints.get(regionKey(a.demand)),bh=hints.get(regionKey(b.demand));
            int c=Double.compare(ah==null ? .5 : ah.yFraction,
                bh==null ? .5 : bh.yFraction);
            if(c!=0)return c;
            return a.demand.componentId.compareTo(b.demand.componentId);
        }});
        for (Item item:orderedAnchors) {
            PcbFootprint selected=mediumPlace(item,placed,topology,hints,board,
                constraints,outline,seed,candidate,true,evaluations);
            if(selected==null) throw new Rejected("MEDIUM_CONNECTOR_PACKING");
            item.footprint=selected; placed.add(selected);
        }
        while(!remaining.isEmpty()) {
            Item next=chooseMediumItem(remaining.values(),placed,topology,board,
                seed,candidate);
            if(next==null) throw new Rejected("MEDIUM_COMPONENT_ORDER");
            PcbFootprint selected=mediumPlace(next,placed,topology,hints,board,
                constraints,outline,seed,candidate,false,evaluations);
            if(selected==null) throw new Rejected("MEDIUM_COMPONENT_PACKING");
            next.footprint=selected; placed.add(selected);
            remaining.remove(next.demand.componentId);
        }
        validate(board,constraints,outline,placed);
        return new Plan(outline,placed,navigationFor(constraints),courtyardArea,
            demandArea,candidate,evaluations[0]);
    }

    private static Vector<PcbLayoutRegion> navigationFor(
            PcbPlacementConstraints constraints) {
        Vector<PcbLayoutRegion> navigation=new Vector<PcbLayoutRegion>();
        TreeMap<String,Vector<String>> members=new TreeMap<String,Vector<String>>();
        TreeMap<String,String> labels=new TreeMap<String,String>();
        for (PcbPlacementConstraints.Part part:constraints.getParts()) {
            Vector<String> ids=members.get(part.regionId);
            if(ids==null){ids=new Vector<String>();members.put(part.regionId,ids);}
            ids.add(part.componentId); labels.put(part.regionId,part.regionLabel);
        }
        for(String id:members.keySet())
            navigation.add(new PcbLayoutRegion(id,labels.get(id),members.get(id)));
        return navigation;
    }

    private static String regionKey(PcbPlacementConstraints.Part part) {
        return part.domainId+"/"+part.regionId;
    }

    private static boolean anchorsOnSide(Vector<Item> anchors,long seed,int candidate,
            PcbPlacementConstraints.Anchor wanted) {
        for(Item item:anchors) if(resolvedAnchor(item.demand,seed,candidate)==wanted)
            return true;
        return false;
    }

    /** Build deterministic graph-derived macro hints for region lanes. */
    private static TreeMap<String,RegionHint> mediumRegionHints(
            TroubleshootBoard board,TreeMap<String,Region> groups,Vector<Item> anchors,
            TreeMap<String,Item> byId,TopologyPlacementGraph topology,long seed,int candidate) {
        TreeMap<String,RegionHint> result=new TreeMap<String,RegionHint>();
        TreeMap<String,TreeMap<String,Double>> edges=
            new TreeMap<String,TreeMap<String,Double>>();
        for(Item item:byId.values()) {
            String key=regionKey(item.demand);
            if(result.containsKey(key))continue;
            result.put(key,new RegionHint(key));
            edges.put(key,new TreeMap<String,Double>());
        }
        for(Item item:byId.values()) {
            String first=regionKey(item.demand);
            Vector<TopologyPlacementGraph.PadLink> links=topology.getLinksFor(
                item.demand.componentId);
            for(TopologyPlacementGraph.PadLink link:links) {
                Item other=byId.get(link.getOtherComponentId());
                if(other==null)continue;
                String second=regionKey(other.demand);
                if(first.equals(second))continue;
                addRegionEdge(edges,first,second,mediumLinkWeight(board,link));
            }
        }
        Vector<String> leftSources=new Vector<String>(),rightSources=new Vector<String>();
        TreeMap<String,String> sourceSides=new TreeMap<String,String>();
        for(Item item:anchors) {
            String key=regionKey(item.demand),side=resolvedAnchor(item.demand,seed,candidate)==
                PcbPlacementConstraints.Anchor.LEFT?"L":"R";
            String old=sourceSides.get(key);
            if(old==null)sourceSides.put(key,side);
            else if(!old.equals(side))sourceSides.put(key,"B");
        }
        for(String key:sourceSides.keySet()) {
            String side=sourceSides.get(key);
            if("L".equals(side)||"B".equals(side))leftSources.add(key);
            if("R".equals(side)||"B".equals(side))rightSources.add(key);
        }
        Vector<String> allSources=new Vector<String>();
        allSources.addAll(leftSources); for(String key:rightSources)
            if(!allSources.contains(key))allSources.add(key);
        Collections.sort(allSources);
        int rotation=allSources.isEmpty()?0:(int)((seed^(seed>>>32)^candidate)&
            0x7fffffffL)%allSources.size();
        if((candidate&1)==1)Collections.reverse(allSources);
        TreeMap<String,Double> sourceLane=new TreeMap<String,Double>();
        for(int index=0;index<allSources.size();index++) {
            String key=allSources.get((index+rotation)%allSources.size());
            sourceLane.put(key,(index+1)/(double)(allSources.size()+1));
        }
        TreeMap<String,TreeMap<String,Double>> distances=new TreeMap<String,TreeMap<String,Double>>();
        for(String source:allSources) distances.put(source,mediumDistances(edges,source));
        for(String key:result.keySet()) {
            double left=mediumNearestDistance(distances,leftSources,key);
            double right=mediumNearestDistance(distances,rightSources,key);
            RegionHint hint=result.get(key);
            if(left==Double.POSITIVE_INFINITY && right==Double.POSITIVE_INFINITY)
                hint.xFraction=.5;
            else if(left==Double.POSITIVE_INFINITY)
                hint.xFraction=Math.max(.55,.88-.06*Math.min(right,5));
            else if(right==Double.POSITIVE_INFINITY)
                hint.xFraction=Math.min(.45,.12+.06*Math.min(left,5));
            else hint.xFraction=left/(left+right);
            if(hint.xFraction<.08)hint.xFraction=.08;
            if(hint.xFraction>.92)hint.xFraction=.92;
            double yWeight=0,ySum=0;
            for(String source:allSources) {
                Double distance=distances.get(source).get(key);
                if(distance==null||distance==Double.POSITIVE_INFINITY)continue;
                double weight=1.0/(1.0+distance);
                ySum+=weight*sourceLane.get(source); yWeight+=weight;
            }
            hint.yFraction=yWeight==0 ? .5 : ySum/yWeight;
            if(hint.yFraction<.10)hint.yFraction=.10;
            if(hint.yFraction>.90)hint.yFraction=.90;
        }
        return result;
    }

    private static void addRegionEdge(TreeMap<String,TreeMap<String,Double>> edges,
            String first,String second,double weight) {
        if(first==null||second==null||first.equals(second)||weight<=0)return;
        TreeMap<String,Double> a=edges.get(first),b=edges.get(second);
        if(a==null||b==null)return;
        Double old=a.get(second);a.put(second,old==null?weight:old+weight);
        old=b.get(first);b.put(first,old==null?weight:old+weight);
    }

    private static TreeMap<String,Double> mediumDistances(
            TreeMap<String,TreeMap<String,Double>> edges,String source) {
        TreeMap<String,Double> result=new TreeMap<String,Double>();
        for(String key:edges.keySet())result.put(key,Double.POSITIVE_INFINITY);
        if(!result.containsKey(source))return result;
        result.put(source,0.0);
        for(int pass=0;pass<edges.size();pass++) {
            boolean changed=false;
            for(String first:edges.keySet()) {
                double base=result.get(first);
                if(base==Double.POSITIVE_INFINITY)continue;
                for(String second:edges.get(first).keySet()) {
                    double weight=edges.get(first).get(second);
                    double next=base+1.0/Math.max(.05,weight);
                    if(next+1e-9<result.get(second)) {
                        result.put(second,next);changed=true;
                    }
                }
            }
            if(!changed)break;
        }
        return result;
    }

    private static double mediumNearestDistance(
            TreeMap<String,TreeMap<String,Double>> distances,Vector<String> sources,
            String key) {
        double result=Double.POSITIVE_INFINITY;
        for(String source:sources) {
            TreeMap<String,Double> values=distances.get(source);
            if(values!=null&&values.get(key)!=null)result=Math.min(result,values.get(key));
        }
        return result;
    }

    private static double mediumLinkWeight(TroubleshootBoard board,
            TopologyPlacementGraph.PadLink link) {
        double weight=link.getWeight();
        BoardNet net=board.getNet(link.getNetId());
        if(net==null)return weight;
        int degree=net.getPadIds().size();
        /* Shared rails distribute; direct two-terminal relationships pull. */
        if(degree>2)weight/=degree;
        BoardComponent first=board.getComponent(link.getComponentId());
        BoardComponent second=board.getComponent(link.getOtherComponentId());
        if(first!=null&&first.getPhysicalPackage().isConnector())weight*=.75;
        if(second!=null&&second.getPhysicalPackage().isConnector())weight*=.75;
        return Math.max(.02,weight);
    }

    private static Item chooseMediumItem(java.util.Collection<Item> values,
            Vector<PcbFootprint> placed,TopologyPlacementGraph topology,
            TroubleshootBoard board,long seed,int candidate) {
        Item best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(Item item:values) {
            double connected=0,links=0,degree=0;
            for(TopologyPlacementGraph.PadLink link:topology.getLinksFor(
                    item.demand.componentId)) {
                degree+=mediumLinkWeight(board,link);
                PcbFootprint other=findPlacedFootprint(placed,link.getOtherComponentId());
                if(other!=null) {
                    connected+=mediumLinkWeight(board,link);links++;
                }
            }
            /* Direct links are the strongest ordering signal. */
            double score=connected*10000+links*100+degree;
            int hash=item.demand.componentId.hashCode()^(int)seed^candidate*31;
            score+=((hash&0x7fffffff)%997)/100000.0;
            if(best==null||score>bestScore) {best=item;bestScore=score;}
        }
        return best;
    }

    private static PcbFootprint mediumPlace(Item item,Vector<PcbFootprint> placed,
            TopologyPlacementGraph topology,TreeMap<String,RegionHint> hints,
            TroubleshootBoard board,PcbPlacementConstraints constraints,Rectangle outline,
            long seed,int candidate,boolean anchor,int[] evaluations) {
        RegionHint hint=hints.get(regionKey(item.demand));
        int preferredX=outline.x+MEDIUM_BORDER+(int)Math.round(
            (outline.width-2.0*MEDIUM_BORDER)*(hint==null ? .5 : hint.xFraction))-
            item.footprint.getPlacement().getWidth()/2;
        int preferredY=outline.y+MEDIUM_BORDER+(int)Math.round(
            (outline.height-2.0*MEDIUM_BORDER)*(hint==null ? .5 : hint.yFraction))-
            item.footprint.getPlacement().getHeight()/2;
        PcbPlacementConstraints.Anchor side=resolvedAnchor(item.demand,seed,candidate);
        if(anchor)preferredX=side==PcbPlacementConstraints.Anchor.RIGHT?
            outline.x+outline.width-MEDIUM_BORDER-item.footprint.getPlacement().getWidth():
            outline.x+MEDIUM_BORDER;
        Point connected=weightedConnectedTarget(item.footprint,placed,
            topology.getLinksFor(item.demand.componentId),preferredX,preferredY);
        int direction=hint==null||hint.xFraction<.45?1:hint.xFraction>.55?-1:0;
        if(anchor)direction=0;
        int baseX=anchor?preferredX:(connected.x*3+preferredX)/4+direction*MEDIUM_CHAIN_GAP;
        int baseY=anchor?(preferredY):(connected.y*3+preferredY)/4;
        PcbFootprint best=null;double bestScore=Double.POSITIVE_INFINITY;
        int maxRing=anchor?24:20;
        for(int ring=0;ring<=maxRing;ring++) {
            int step=ring*MEDIUM_STEP;
            int[] dx=anchor?new int[]{0,0,0,0,0}:new int[]{direction*step,-direction*step,0,0,
                direction*step,-direction*step};
            int[] dy=anchor?new int[]{0,step,-step,step*2,-step*2}:
                new int[]{0,0,step,-step,step,-step};
            int count=Math.min(dx.length,dy.length);
            for(int index=0;index<count;index++) {
                int x=anchor?preferredX:baseX+dx[index],y=anchor?baseY+dy[index]:baseY+dy[index];
                x=mediumGridOrigin(item.footprint,x,outline,itemsMargin(item,constraints),false);
                y=mediumGridOrigin(item.footprint,y,outline,itemsMargin(item,constraints),true);
                if(anchor&&side==PcbPlacementConstraints.Anchor.RIGHT)
                    x=mediumGridOrigin(item.footprint,preferredX,outline,
                        itemsMargin(item,constraints),false);
                PcbFootprint trial=item.footprint.translated(x,y);
                evaluations[0]++;
                if(evaluations[0]>MEDIUM_MAX_EVALUATIONS)
                    throw new Rejected("MEDIUM_PLACEMENT_BUDGET");
                if(!fits(trial,outline,placed,-1,constraints))continue;
                double score=mediumPlacementScore(trial,item,placed,topology,board,hint,outline);
                if(score<bestScore){best=trial;bestScore=score;}
            }
        }
        /*
         * A connected target can be surrounded by earlier packages even when
         * the board still has ample free area.  Search a bounded, seeded grid
         * before rejecting the candidate so one crowded chain does not discard
         * an otherwise useful outline.
         */
        if(best==null) {
            int margin=itemsMargin(item,constraints);
            int minX=mediumGridBound(item.footprint,outline,margin,false,false);
            int maxX=mediumGridBound(item.footprint,outline,margin,false,true);
            int minY=mediumGridBound(item.footprint,outline,margin,true,false);
            int maxY=mediumGridBound(item.footprint,outline,margin,true,true);
            int columns=Math.max(1,(maxX-minX)/MEDIUM_STEP+1);
            int rows=Math.max(1,(maxY-minY)/MEDIUM_STEP+1);
            long total=(long)columns*rows;
            long hash=item.demand.componentId.hashCode()^(seed*0x9e3779b97f4a7c15L)^
                (long)(candidate+1)*0x632be59bd9b4e019L;
            int start=(int)((hash^(hash>>>32))&0x7fffffffL);
            if(total>0)start=(int)(start%total);
            for(long visit=0;visit<total;visit++) {
                if(evaluations[0]>=MEDIUM_MAX_EVALUATIONS)break;
                int index=(int)((start+visit)%total);
                int row=index/columns,col=index%columns;
                if((row&1)!=0)col=columns-1-col;
                int x=minX+col*MEDIUM_STEP,y=minY+row*MEDIUM_STEP;
                PcbFootprint trial=item.footprint.translated(
                    mediumGridOrigin(item.footprint,x,outline,margin,false),
                    mediumGridOrigin(item.footprint,y,outline,margin,true));
                evaluations[0]++;
                if(!fits(trial,outline,placed,-1,constraints))continue;
                double score=mediumPlacementScore(trial,item,placed,topology,board,hint,outline);
                if(score<bestScore){best=trial;bestScore=score;}
                /* The fallback only needs one legal escape from a crowded
                 * target.  Keeping the first seeded hit bounds worst-case
                 * work for larger inventories and preserves candidate variety. */
                break;
            }
        }
        return best;
    }

    private static int itemsMargin(Item item,PcbPlacementConstraints constraints) {
        return item.demand.accessMargin;
    }

    private static int mediumGridOrigin(PcbFootprint source,int origin,Rectangle outline,
            int margin,boolean vertical) {
        int minGrid=mediumGridBound(source,outline,margin,vertical,false);
        int maxGrid=mediumGridBound(source,outline,margin,vertical,true);
        int aligned=(int)Math.round(origin/10.0)*10;
        if(maxGrid<minGrid)return aligned;
        return Math.max(minGrid,Math.min(maxGrid,aligned));
    }

    private static int mediumGridBound(PcbFootprint source,Rectangle outline,int margin,
            boolean vertical,boolean upper) {
        Rectangle local=envelope(source,margin);
        int base=vertical?source.getPlacement().getY():source.getPlacement().getX();
        int edge=vertical?outline.y:outline.x;
        int size=vertical?outline.height:outline.width;
        int localEdge=vertical?local.y:local.x;
        int localEnd=vertical?local.y+local.height:local.x+local.width;
        int min=edge+10-(localEdge-base),max=edge+size-10-(localEnd-base);
        return upper?(max/10)*10:align(min);
    }

    private static double mediumPlacementScore(PcbFootprint footprint,Item item,
            Vector<PcbFootprint> placed,TopologyPlacementGraph topology,
            TroubleshootBoard board,RegionHint hint,Rectangle outline) {
        double score=0;
        for(TopologyPlacementGraph.PadLink link:topology.getLinksFor(
                item.demand.componentId)) {
            PcbFootprint other=findPlacedFootprint(placed,link.getOtherComponentId());
            if(other==null)continue;
            PcbPadPlacement a=footprint.getPad(link.getPadId()),b=other.getPad(link.getOtherPadId());
            score+=(Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY()))*
                mediumLinkWeight(board,link);
            Rectangle corridor=mediumCorridor(a,b);
            for(PcbFootprint third:placed) {
                if(third.getPlacement().getComponentId().equals(link.getOtherComponentId()))continue;
                if(corridor.intersects(third.getPlacement().getRoutingCourtyard())&&
                        !mediumSharesNet(topology,third.getPlacement().getComponentId(),link.getNetId()))
                    score+=12000*mediumLinkWeight(board,link);
            }
        }
        if(hint!=null) {
            double cx=footprint.getPlacement().getX()+footprint.getPlacement().getWidth()/2.0;
            double cy=footprint.getPlacement().getY()+footprint.getPlacement().getHeight()/2.0;
            double hx=outline.x+MEDIUM_BORDER+
                (outline.width-2.0*MEDIUM_BORDER)*hint.xFraction;
            double hy=outline.y+MEDIUM_BORDER+
                (outline.height-2.0*MEDIUM_BORDER)*hint.yFraction;
            score+=(Math.abs(cx-hx)+Math.abs(cy-hy))*.02;
        }
        return score;
    }

    private static Rectangle mediumCorridor(PcbPadPlacement a,PcbPadPlacement b) {
        int left=Math.min(a.getX(),b.getX())-10,top=Math.min(a.getY(),b.getY())-10;
        int width=Math.max(20,Math.abs(a.getX()-b.getX())+20);
        int height=Math.max(20,Math.abs(a.getY()-b.getY())+20);
        return new Rectangle(left,top,width,height);
    }

    private static boolean mediumSharesNet(TopologyPlacementGraph topology,
            String componentId,String netId) {
        for(TopologyPlacementGraph.PadLink link:topology.getLinksFor(componentId))
            if(link.getNetId().equals(netId))return true;
        return false;
    }

    private static PcbPlacementConstraints.Anchor resolvedAnchor(PcbPlacementConstraints.Part part,
            long seed, int candidate) {
        if (part.anchor != PcbPlacementConstraints.Anchor.EDGE) return part.anchor;
        long value = seed ^ (0x9e3779b97f4a7c15L * (candidate + 1L)) ^
            (0xbf58476d1ce4e5b9L * part.componentId.hashCode());
        value ^= value >>> 30;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return (value & 1L) == 0 ? PcbPlacementConstraints.Anchor.LEFT :
            PcbPlacementConstraints.Anchor.RIGHT;
    }

    private static int anchorOffset(long seed, int candidate, boolean right) {
        long value = seed ^ (right ? 0xd1b54a32d192ed03L : 0x8cb92baa3f3d8dd7L) ^
            (0x632be59bd9b4e019L * (candidate + 1L));
        value ^= value >>> 29;
        int bucket = (int)(value & 7L);
        return bucket * 10;
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
