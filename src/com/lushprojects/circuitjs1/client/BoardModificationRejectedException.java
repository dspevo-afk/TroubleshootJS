package com.lushprojects.circuitjs1.client;

class BoardModificationRejectedException extends IllegalStateException {
    BoardModificationRejectedException(String message) {
        super(message);
    }
}