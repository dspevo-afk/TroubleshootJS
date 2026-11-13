package com.lushprojects.circuitjs1.client;

/**
 * Optional family view of latched board inputs. The workbench uses it only to
 * show which input button is currently applied; it never changes the inputs.
 */
interface GeneratedActiveInputOperations {
    /** True when this operation's input setting is the one the board has now. */
    boolean isInputOperationActive(String operationId);
}
