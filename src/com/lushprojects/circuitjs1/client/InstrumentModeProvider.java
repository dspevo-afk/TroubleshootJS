package com.lushprojects.circuitjs1.client;

/**
 * Production extension contract for one instrument mode.
 *
 * Provider-owned identity, behavior, and lifecycle remain on the strategy
 * contract; this marker distinguishes providers that may enter the production
 * composition seam from developer-only verification strategies.
 */
interface InstrumentModeProvider extends InstrumentModeStrategy {
}
