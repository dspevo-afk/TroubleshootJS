package com.lushprojects.circuitjs1.client;

/** Engine-independent render/input lifecycle. Implementations receive only a read-only physical scene. */
interface WorkbenchRenderBackend {
    enum Intent { SELECT, PROBE, DROP }
    void attach(WorkbenchPhysicalScene scene, Object attachment);
    void present(WorkbenchPhysicalScene scene, Rectangle viewport);
    WorkbenchRenderHit hitTest(int x, int y, Intent intent);
    Point marker(WorkbenchRenderHit.Kind kind,String id,String secondaryId,int terminal);
    Point project(int x, int y, boolean board);
    void detach();
}
