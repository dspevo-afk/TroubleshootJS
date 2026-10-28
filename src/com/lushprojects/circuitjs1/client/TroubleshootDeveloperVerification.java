package com.lushprojects.circuitjs1.client;

/** Developer-only generated verification dispatch. CirSim owns all live state. */
final class TroubleshootDeveloperVerification {
    private TroubleshootDeveloperVerification() { }

    static void run(final CirSim sim) {
	    if (!sim.developerVerifierRunning &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null &&
		sim.generatedChallengeController.isReady()) {
		sim.developerVerifierRunning = true;
		try {
		    if (sim.troubleshootA01Measurement &&
			!sim.troubleshootA01MeasurementComplete) {
			sim.troubleshootA01MeasurementComplete = true;
			CirSim.publishBrowserVerificationResult("RUNNING:a01");
			try {
			    sim.publishA01EvidenceForDeveloperVerification(A01MeasurementVerifier.verify(sim,
				sim.troubleshootA01Corpus, sim.troubleshootA01Round,
				sim.troubleshootA01SourceFingerprint,
				sim.troubleshootA01BuildFingerprint,
				sim.troubleshootA01ForcedFailure));
			    CirSim.publishBrowserVerificationResult("PASS:a01");
			} catch (Throwable failure) {
			    CirSim.publishBrowserVerificationResult("FAIL:a01:" + failure.getMessage());
			    if (failure instanceof Error) throw (Error) failure;
			    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
			    throw new IllegalStateException("A01 measurement failed", failure);
			}
		    }
		    if (sim.troubleshootResistanceVerification &&
			sim.troubleshootResistanceVerifierState == CirSim.VERIFIER_NOT_STARTED) {
			try {
			    sim.troubleshootResistanceVerifierState = CirSim.VERIFIER_RUNNING;
			    ResistanceMeasurementDeveloperVerifier.verify(sim);
			    sim.troubleshootResistanceVerifierState = CirSim.VERIFIER_PASSED;
			    CirSim.publishBrowserVerificationResult("PASS:resistance");
			} catch (RuntimeException e) {
			    sim.troubleshootResistanceVerifierState = CirSim.VERIFIER_FAILED;
			    CirSim.console("Resistance verification failed: " + e.getMessage());
			    throw e;
			}
		    }
		    if (sim.troubleshootChallengeVerification && !sim.troubleshootChallengeVerificationComplete) {
			sim.troubleshootChallengeVerificationComplete = true;
			ChallengeDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:challenge");
		    }
		    if (sim.troubleshootReplacementVerification && !sim.troubleshootReplacementVerificationComplete) {
			sim.troubleshootReplacementVerificationComplete = true;
			ReplacementDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:replacement");
		    }
		    if (sim.troubleshootWrongRepairVerification && !sim.troubleshootWrongRepairVerificationComplete) {
			sim.troubleshootWrongRepairVerificationComplete = true;
			ReplacementDeveloperVerifier.verifyWrongRepair(sim);
			CirSim.publishBrowserVerificationResult("PASS:wrong-repair");
		    }
		    if (sim.troubleshootMeterVerification && !sim.troubleshootMeterVerificationComplete) {
			sim.troubleshootMeterVerificationComplete = true;
			MeterLifecycleDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:meter");
		    }
		    if (sim.troubleshootDiodeVerification && !sim.troubleshootDiodeVerificationComplete) {
			sim.troubleshootDiodeVerificationComplete = true;
			DiodeFamilyDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:diode");
		    }
			if (sim.troubleshootLedPhysicalVerification &&
			    !sim.troubleshootLedPhysicalVerificationComplete) {
			sim.troubleshootLedPhysicalVerificationComplete = true;
			LedPhysicalDeveloperVerifier.verify(sim);
			    CirSim.publishBrowserVerificationResult("PASS:led-parts");
			}
			if (sim.troubleshootParallelVerification && !sim.troubleshootParallelVerificationComplete) {
			    sim.troubleshootParallelVerificationComplete = true;
			    ParallelDualIndicatorDeveloperVerifier.verify(sim);
			    CirSim.publishBrowserVerificationResult("PASS:parallel");
			}
		    if (sim.troubleshootStressVerification && !sim.troubleshootStressVerificationComplete &&
				(!sim.troubleshootStressVerificationDeferred || sim.troubleshootStressVerificationRequested)) {
				sim.troubleshootStressVerificationComplete = true;
				ResistorStressDamageDeveloperVerifier.verify(sim);
				CirSim.publishBrowserVerificationResult("PASS:stress");
			}
		    if (sim.troubleshootLayoutVerification && !sim.troubleshootGeometryVerificationComplete) {
			sim.troubleshootGeometryVerificationComplete = true;
			PcbLayoutDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:layout");
		    }
		    if (sim.troubleshootArchitectureVerification &&
			!sim.troubleshootArchitectureVerificationComplete) {
			sim.troubleshootArchitectureVerificationComplete = true;
			ArchitectureDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:architecture");
		    }
		    if (sim.troubleshootQuickPlayVerification &&
			!sim.troubleshootQuickPlayVerificationComplete) {
			sim.troubleshootQuickPlayVerificationComplete = true;
			if (sim.quickPlayActive)
			    QuickPlayDeveloperVerifier.verify(sim);
			else
			    QuickPlayDeveloperVerifier.verifyExplicitRoute(sim);
			CirSim.publishBrowserVerificationResult(sim.quickPlayActive ?
			    "PASS:quick-play" : "PASS:quick-play-explicit");
		    }
		    // Temporal admission recursively updates while its outer verification
		    // still owns generatedVerificationRunning. The physical meter proof
		    // must start only after that outer operation has fully returned.
		    if (sim.troubleshootStoredEnergyVerification &&
			    !sim.troubleshootStoredEnergyVerificationComplete && sim.isGeneratedRuntimeSettled()) {
			sim.troubleshootStoredEnergyVerificationComplete = true;
			StoredEnergyDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:stored-energy");
		    }
		    if (sim.troubleshootRcVerification && !sim.troubleshootRcVerificationComplete) {
			sim.troubleshootRcVerificationComplete = true;
			RcDelayDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:rc");
		    }
		    if (sim.troubleshootNpnVerification && !sim.troubleshootNpnVerificationComplete) {
			sim.troubleshootNpnVerificationComplete = true;
			NpnLowSideSwitchDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:npn");
		    }
		    if (sim.troubleshootNmosVerification && !sim.troubleshootNmosVerificationComplete) {
			sim.troubleshootNmosVerificationComplete = true;
			NmosLowSideSwitchDeveloperVerifier.verify(sim);
			CirSim.publishBrowserVerificationResult("PASS:nmos");
		    }
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.troubleshootTask39Verification &&
		!sim.troubleshootTask39VerificationComplete &&
		(sim.generatedChallengeController == null || sim.generatedChallengeController.isReady())) {
		sim.developerVerifierRunning = true;
		try {
		    sim.troubleshootTask39VerificationComplete = true;
		    Task39DeveloperVerifier.verify(sim);
		    CirSim.publishBrowserVerificationResult("PASS:task39");
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.troubleshootTask40Verification &&
		!sim.troubleshootTask40VerificationComplete &&
		(sim.generatedChallengeController == null || sim.generatedChallengeController.isReady())) {
		sim.developerVerifierRunning = true;
		try {
		    sim.troubleshootTask40VerificationComplete = true;
		    Task40DeveloperVerifier.verify(sim);
		    CirSim.publishBrowserVerificationResult("PASS:task40");
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootTask41Verification &&
		!sim.troubleshootTask41VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		(sim.generatedChallengeController == null || sim.generatedChallengeController.isReady())) {
		sim.developerVerifierRunning = true;
		try {
		    CirSim.publishBrowserVerificationResult("RUNNING:task41");
		    sim.troubleshootTask41VerificationComplete = true;
		    Task41DeveloperVerifier.verify(sim);
		    CirSim.publishBrowserVerificationResult("PASS:task41");
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootTask48Verification &&
		!sim.troubleshootTask48VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
		sim.isGeneratedRuntimeSettled()) {
		sim.developerVerifierRunning = true;
		sim.troubleshootTask48VerificationComplete = true;
		CirSim.publishBrowserVerificationResult("RUNNING:task48");
		try {
		    CirSim.publishTask48Evidence(Task48DeveloperVerifier.verify(sim,
			sim.troubleshootTask48Seed, sim.troubleshootTask48ForcedFailure));
		    CirSim.publishBrowserVerificationResult("PASS:task48");
		} catch (Throwable failure) {
		    CirSim.publishBrowserVerificationResult("FAIL:task48:" + failure.getMessage());
		    if (failure instanceof Error) throw (Error) failure;
		    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		    throw new IllegalStateException("Task 48 verification failed", failure);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootTask49Verification &&
		!sim.troubleshootTask49VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
		sim.isGeneratedRuntimeSettled()) {
		sim.developerVerifierRunning = true;
		sim.troubleshootTask49VerificationComplete = true;
		CirSim.publishBrowserVerificationResult("RUNNING:task49");
		try {
		    CirSim.publishTask49Evidence(Task49DeveloperVerifier.verify(sim,
			sim.troubleshootTask49Seed, sim.troubleshootTask49ForcedFailure));
		    CirSim.publishBrowserVerificationResult("PASS:task49");
		} catch (Throwable failure) {
		    CirSim.publishBrowserVerificationResult("FAIL:task49:" + failure.getMessage());
		    if (failure instanceof Error) throw (Error) failure;
		    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		    throw new IllegalStateException("Task 49 verification failed", failure);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootA03Verification &&
		!sim.troubleshootA03VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
		sim.isGeneratedRuntimeSettled()) {
		sim.developerVerifierRunning = true;
		sim.troubleshootA03VerificationComplete = true;
		CirSim.publishBrowserVerificationResult("RUNNING:a03");
		try {
		    CirSim.publishA03Evidence(A03IdentityDeveloperVerifier.verify(sim,
			sim.troubleshootA03ForcedFailure));
		    CirSim.publishBrowserVerificationResult("PASS:a03");
		} catch (Throwable failure) {
		    CirSim.publishBrowserVerificationResult("FAIL:a03:" + failure.getMessage());
		    if (failure instanceof Error) throw (Error) failure;
		    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		    throw new IllegalStateException("A03 verification failed", failure);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
            if (!sim.developerVerifierRunning && sim.troubleshootP01Verification && !sim.troubleshootP01VerificationComplete &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                    sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                    sim.isGeneratedRuntimeSettled()) {
                sim.developerVerifierRunning = true; sim.troubleshootP01VerificationComplete = true;
                CirSim.publishBrowserVerificationResult("RUNNING:p01");
                try {
                    P01PhysicalPoseDeveloperVerifier.verify(sim, sim.troubleshootP01ForcedFailure);
                    CirSim.publishBrowserVerificationResult("PASS:p01");
                } catch (Throwable failure) {
                    CirSim.publishBrowserVerificationResult("FAIL:p01:" + failure.getMessage());
                    if (failure instanceof Error) throw (Error)failure;
                    if (failure instanceof RuntimeException) throw (RuntimeException)failure;
                    throw new IllegalStateException("P01 verification failed", failure);
                } finally { sim.developerVerifierRunning = false; }
            }
            if (!sim.developerVerifierRunning && sim.troubleshootA08Verification && !sim.troubleshootA08VerificationComplete &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                    sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                    sim.isGeneratedRuntimeSettled()) {
                sim.developerVerifierRunning = true; sim.troubleshootA08VerificationComplete = true;
                CirSim.publishBrowserVerificationResult("RUNNING:a08");
                A08MutationDeveloperVerifier.start(sim, sim.troubleshootA08ForcedFailure);
            }
            if (!sim.developerVerifierRunning && sim.troubleshootE06Verification && !sim.troubleshootE06VerificationComplete &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                    sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                    sim.isGeneratedRuntimeSettled()) {
                sim.developerVerifierRunning = true; sim.troubleshootE06VerificationComplete = true;
                CirSim.publishBrowserVerificationResult("RUNNING:e06");
                E06ConverterDeveloperVerifier.start(sim, sim.troubleshootE06ForcedFailure);
            }
            if (!sim.developerVerifierRunning && sim.troubleshootE05Verification && !sim.troubleshootE05VerificationComplete &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                    sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                    sim.isGeneratedRuntimeSettled()) {
                sim.developerVerifierRunning = true; sim.troubleshootE05VerificationComplete = true;
                CirSim.publishBrowserVerificationResult("RUNNING:e05");
                E05ElectricalDeveloperVerifier.start(sim, sim.troubleshootE05ForcedFailure);
            }
            if (!sim.developerVerifierRunning && sim.troubleshootA07Verification && !sim.troubleshootA07VerificationComplete &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                    sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                    sim.isGeneratedRuntimeSettled()) {
                sim.developerVerifierRunning = true; sim.troubleshootA07VerificationComplete = true;
                CirSim.publishBrowserVerificationResult("RUNNING:a07");
                A07SolverDeveloperVerifier.start(sim, sim.troubleshootA07ForcedFailure);
            }
            if (!sim.developerVerifierRunning && sim.troubleshootU02U03Verification &&
                    !sim.troubleshootU02U03VerificationComplete &&
                    !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                    sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                    sim.isGeneratedRuntimeSettled()) {
                sim.developerVerifierRunning = true;
                sim.troubleshootU02U03VerificationComplete = true;
                CirSim.publishBrowserVerificationResult("RUNNING:u02-u03");
                U02U03TemporalDeveloperVerifier.start(sim, sim.troubleshootTemporalFixture,
                    sim.troubleshootU02U03VisualHold,
                    new U02U03TemporalDeveloperVerifier.Completion() {
                        public void finished(String report, Throwable failure) {
                            sim.developerVerifierRunning = false;
                            U02U03TemporalDeveloperVerifier.publish(report, failure);
                            CirSim.publishBrowserVerificationResult(failure == null ? "PASS:u02-u03" :
                                "FAIL:u02-u03:" + failure.getMessage());
                        }
                    });
            }
	    if (!sim.developerVerifierRunning && sim.troubleshootE03Verification &&
	        !sim.troubleshootQ15Verification &&
                !sim.troubleshootE03VerificationComplete &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                sim.isGeneratedRuntimeSettled()) {
            sim.developerVerifierRunning = true;
            sim.troubleshootE03VerificationComplete = true;
            CirSim.publishBrowserVerificationResult("RUNNING:e03");
            E03RelayDeveloperVerifier.start(sim,sim.troubleshootE03ForcedFailure,new E03RelayDeveloperVerifier.Completion() {
                public void finished(String report,Throwable failure) {
                    sim.developerVerifierRunning=false;
                    if(failure==null) {CirSim.publishE03Evidence(report);CirSim.publishBrowserVerificationResult("PASS:e03");}
                    else {CirSim.publishBrowserVerificationResult("FAIL:e03:"+failure.getMessage());CirSim.console("E03 failure: "+failure);}
                }
            });
        }
        if (!sim.developerVerifierRunning && sim.troubleshootServiceVerification && !sim.troubleshootServiceComplete &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() && sim.generatedChallengeController != null &&
                sim.generatedChallengeController.isReady() && sim.isGeneratedRuntimeSettled()) {
            sim.developerVerifierRunning = true; sim.troubleshootServiceComplete = true;
            CirSim.publishBrowserVerificationResult("RUNNING:service");
            FaultBlindManipulationVerifier.start(sim, sim.troubleshootServiceForced, new FaultBlindManipulationVerifier.Completion() {
                public void finished(String report, Throwable failure) {
                    sim.developerVerifierRunning = false; FaultBlindManipulationVerifier.publish(report);
                    CirSim.publishBrowserVerificationResult(failure == null ? "PASS:service" : "FAIL:service:" + failure.getMessage());
                }
            });
        }
        if (!sim.developerVerifierRunning && sim.troubleshootRender0Verification && !sim.troubleshootRender0Complete &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() && sim.generatedChallengeController != null &&
                sim.generatedChallengeController.isReady() && sim.isGeneratedRuntimeSettled()) {
            sim.developerVerifierRunning = true; sim.troubleshootRender0Complete = true;
            CirSim.publishBrowserVerificationResult("RUNNING:render0");
            Render0DeveloperVerifier.start(sim, sim.troubleshootRender0Forced, new Render0DeveloperVerifier.Completion() {
                public void finished(String report, Throwable failure) {
                    sim.developerVerifierRunning = false; Render0DeveloperVerifier.publish(report);
                    CirSim.publishBrowserVerificationResult(failure == null ? "PASS:render0" : "FAIL:render0:" + failure.getMessage());
                }
            });
        }
        if (!sim.developerVerifierRunning && sim.troubleshootAlphaVerification && !sim.troubleshootAlphaComplete &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() && sim.generatedChallengeController != null &&
                sim.generatedChallengeController.isReady() && sim.isGeneratedRuntimeSettled()) {
            sim.developerVerifierRunning = true; sim.troubleshootAlphaComplete = true;
            CirSim.publishBrowserVerificationResult("RUNNING:alpha");
            AlphaDeveloperVerifier.start(sim, sim.troubleshootAlphaPilot, sim.troubleshootAlphaForced, sim.troubleshootAlphaCase, new AlphaDeveloperVerifier.Completion() {
                public void finished(String report, Throwable failure) {
                    sim.developerVerifierRunning = false; AlphaDeveloperVerifier.publish(report);
                    CirSim.publishBrowserVerificationResult(failure == null ? "PASS:alpha" : "FAIL:alpha:" + failure.getMessage());
                }
            });
        }
        if (!sim.developerVerifierRunning && sim.troubleshootQuickPlayGateVerification && !sim.troubleshootQuickPlayGateInstalled &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() && sim.generatedChallengeController!=null &&
                sim.generatedChallengeController.isReady() && sim.isGeneratedRuntimeSettled()) {
            sim.troubleshootQuickPlayGateInstalled=true;
            QuickPlayGateDeveloperVerifier.install(sim);
        }
        if (!sim.developerVerifierRunning && sim.troubleshootQ15Verification && !sim.troubleshootQ15VerificationComplete &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() && sim.generatedChallengeController!=null &&
                sim.generatedChallengeController.isReady() && sim.isGeneratedRuntimeSettled()) {
            sim.developerVerifierRunning=true;sim.troubleshootQ15VerificationComplete=true;
            CirSim.publishBrowserVerificationResult("RUNNING:q15");
            Q15ControlBoardDeveloperVerifier.start(sim,sim.troubleshootQ15ForcedFailure,new Q15ControlBoardDeveloperVerifier.Completion() {
                public void finished(String report,Throwable failure) {
                    sim.developerVerifierRunning=false;
                    CirSim.publishQ15Evidence(report);
                    if(failure==null) {CirSim.publishBrowserVerificationResult("PASS:q15");}
                    else {CirSim.publishBrowserVerificationResult("FAIL:q15:"+failure.getMessage());CirSim.console("Q15 failure: "+failure);}
                }
            });
        }
        if (!sim.developerVerifierRunning && sim.troubleshootE01Verification &&
                !sim.troubleshootE01VerificationComplete &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                sim.isGeneratedRuntimeSettled()) {
            sim.developerVerifierRunning = true;
            sim.troubleshootE01VerificationComplete = true;
            CirSim.publishBrowserVerificationResult("RUNNING:e01");
            try {
                CirSim.publishE01Evidence(E01SourceDeveloperVerifier.verify(sim,sim.troubleshootE01ForcedFailure));
                CirSim.publishBrowserVerificationResult("PASS:e01");
            } catch (Throwable failure) {
                CirSim.publishBrowserVerificationResult("FAIL:e01:" + failure.getMessage());
                if (failure instanceof Error) throw (Error)failure;
                if (failure instanceof RuntimeException) throw (RuntimeException)failure;
                throw new IllegalStateException("E01 verification failed",failure);
            } finally { sim.developerVerifierRunning = false; }
        }
	 if (!sim.developerVerifierRunning && sim.troubleshootE02Verification &&
		 !sim.troubleshootE02VerificationComplete &&
		 !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		 sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
		 sim.isGeneratedRuntimeSettled()) {
	     sim.developerVerifierRunning = true;
	     sim.troubleshootE02VerificationComplete = true;
	     CirSim.publishBrowserVerificationResult("RUNNING:e02");
	     try {
		 CirSim.publishE02Evidence(E02RegulatorDeveloperVerifier.verify(sim,
		     sim.troubleshootE02ForcedFailure));
		 CirSim.publishBrowserVerificationResult("PASS:e02");
	     } catch (Throwable failure) {
		 CirSim.publishBrowserVerificationResult("FAIL:e02:" + failure.getMessage());
		 // The explicit negative canary is terminal evidence, like A07's
		 // forced failure. Unexpected solver or verifier errors still escape.
		 if (!(sim.troubleshootE02ForcedFailure && failure instanceof AssertionError &&
		         "e02-explicit-failure-canary".equals(failure.getMessage()))) {
		     if (failure instanceof Error) throw (Error)failure;
		     if (failure instanceof RuntimeException) throw (RuntimeException)failure;
		     throw new IllegalStateException("E02 verification failed", failure);
		 }
	     } finally { sim.developerVerifierRunning = false; }
	 }
	    if (!sim.developerVerifierRunning && sim.troubleshootA06Verification &&
                !sim.troubleshootA06VerificationComplete &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
                sim.isGeneratedRuntimeSettled()) {
            sim.developerVerifierRunning = true;
            sim.troubleshootA06VerificationComplete = true;
            CirSim.publishBrowserVerificationResult("RUNNING:a06");
            try {
                CirSim.publishA06Evidence(A06PowerDeveloperVerifier.verify(sim,sim.troubleshootA06ForcedFailure));
                CirSim.publishBrowserVerificationResult("PASS:a06");
            } catch (Throwable failure) {
                CirSim.publishBrowserVerificationResult("FAIL:a06:" + failure.getMessage());
                if (failure instanceof Error) throw (Error)failure;
                if (failure instanceof RuntimeException) throw (RuntimeException)failure;
                throw new IllegalStateException("A06 verification failed",failure);
            } finally { sim.developerVerifierRunning = false; }
        }
	    if (!sim.developerVerifierRunning && sim.troubleshootA04Verification &&
		!sim.troubleshootA04VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
		sim.isGeneratedRuntimeSettled()) {
		sim.developerVerifierRunning = true;
		sim.troubleshootA04VerificationComplete = true;
		CirSim.publishBrowserVerificationResult("RUNNING:a04");
		try {
		    CirSim.publishA04Evidence(A04ConstructionDeveloperVerifier.verify(sim,
			sim.troubleshootA04ForcedFailure));
		    CirSim.publishBrowserVerificationResult("PASS:a04");
		} catch (Throwable failure) {
		    CirSim.publishBrowserVerificationResult("FAIL:a04:" + failure.getMessage());
		    if (failure instanceof Error) throw (Error) failure;
		    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		    throw new IllegalStateException("A04 verification failed", failure);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootA02Verification &&
		!sim.troubleshootA02VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady()) {
		sim.developerVerifierRunning = true;
		sim.troubleshootA02VerificationComplete = true;
		CirSim.publishBrowserVerificationResult("RUNNING:a02");
		try {
		    CirSim.publishA02Evidence(A02CorrectnessDeveloperVerifier.verify(sim,
			sim.troubleshootA02ForcedFailure));
		    CirSim.publishBrowserVerificationResult("PASS:a02");
		} catch (Throwable failure) {
		    CirSim.publishBrowserVerificationResult("FAIL:a02:" + failure.getMessage());
		    if (failure instanceof Error) throw (Error) failure;
		    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		    throw new IllegalStateException("A02 verification failed", failure);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootTask47Verification &&
		!sim.troubleshootTask47VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
		sim.isGeneratedRuntimeSettled()) {
		sim.developerVerifierRunning = true;
		sim.troubleshootTask47VerificationComplete = true;
		CirSim.publishBrowserVerificationResult("RUNNING:task47");
		try {
		    CirSim.publishTask47Evidence(Task47AssemblyDeveloperVerifier.verify(sim,
			sim.troubleshootTask47Seed, sim.troubleshootTask47ForcedFailure));
		    CirSim.publishBrowserVerificationResult("PASS:task47");
		} catch (Throwable failure) {
		    CirSim.publishBrowserVerificationResult("FAIL:task47:" + failure.getMessage());
		    if (failure instanceof Error) throw (Error) failure;
		    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		    throw new IllegalStateException("Task 47 assembly verification failed", failure);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootCompositionGateVerification &&
		!sim.troubleshootCompositionGateVerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady() &&
		sim.isGeneratedRuntimeSettled()) {
		sim.developerVerifierRunning = true;
		sim.troubleshootCompositionGateVerificationComplete = true;
		CirSim.publishBrowserVerificationResult("RUNNING:composition-gate");
		try {
		    String lifecycle = CompositionEntryGateDeveloperVerifier.verify(sim);
		    String mutation = CompositionMutationGateDeveloperVerifier.verify(sim);
		    CirSim.publishCompositionGateEvidence(lifecycle + "\n" + mutation);
		    CirSim.publishBrowserVerificationResult("PASS:composition-gate");
		} catch (Throwable failure) {
		    CirSim.publishBrowserVerificationResult("FAIL:composition-gate:" + failure.getMessage());
		    if (failure instanceof Error) throw (Error) failure;
		    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		    throw new IllegalStateException("Composition gate verification failed", failure);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootTask46Verification &&
		!sim.troubleshootTask46VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		sim.generatedChallengeController != null && sim.generatedChallengeController.isReady()) {
		sim.developerVerifierRunning = true;
		try {
		    sim.troubleshootTask46VerificationComplete = true;
		    CirSim.publishBrowserVerificationResult("RUNNING:task46");
		    String parity = Task46ContractVectors.run();
		    Task46ReplayDeveloperVerifier.Result replay = Task46ReplayDeveloperVerifier.verify(sim);
		    sim.publishTask46Evidence(parity, replay);
		    CirSim.publishBrowserVerificationResult("PASS:task46");
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootTask43Verification &&
		!sim.troubleshootTask43VerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		(sim.generatedChallengeController == null || sim.generatedChallengeController.isReady())) {
		sim.developerVerifierRunning = true;
		try {
		    sim.troubleshootTask43VerificationComplete = true;
		    Task43DeveloperVerifier.verify(sim);
		    CirSim.publishBrowserVerificationResult("PASS:task43");
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
	    if (!sim.developerVerifierRunning && sim.troubleshootTask43PVerification &&
		!sim.troubleshootTask43PVerificationComplete &&
		!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
		(sim.generatedChallengeController == null || sim.generatedChallengeController.isReady())) {
		sim.developerVerifierRunning = true;
		try {
		    CirSim.publishBrowserVerificationResult("RUNNING:task43p");
		    sim.troubleshootTask43PVerificationComplete = true;
		    Task43PDeveloperVerifier.verify(sim);
		} finally {
		    sim.developerVerifierRunning = false;
		}
	    }
    }
}
